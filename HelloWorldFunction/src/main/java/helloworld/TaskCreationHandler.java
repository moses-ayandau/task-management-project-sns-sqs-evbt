package helloworld;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

import helloworld.repository.UserRepository;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

public class TaskCreationHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final SqsClient sqsClient;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final String taskQueueUrl;

    public TaskCreationHandler() {
        this.sqsClient = SqsClient.builder().build();
        this.userRepository = new UserRepository();
        this.objectMapper = new ObjectMapper();
        this.taskQueueUrl = System.getenv("TASK_QUEUE_URL");
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        try {
            // Validate that the request comes from an authenticated admin user
            // String userId = CognitoAuthorizer.getUserId(input);
            // User currentUser = userRepository.getUserById(userId);
            
            // if (currentUser == null) {
            //     return ApiGatewayResponseUtil.buildErrorResponse(404, "User not found");
            // }
            
            // if (!"admin".equals(currentUser.getRole())) {
            //     return ApiGatewayResponseUtil.buildErrorResponse(403, "Only administrators can create tasks");
            // }

            // Parse the request body to create a new task
            Map<String, Object> requestBody = objectMapper.readValue(input.getBody(), Map.class);
            
            // Validate required fields
            if (!requestBody.containsKey("name") || !requestBody.containsKey("assignedTo") || 
                !requestBody.containsKey("deadline") || !requestBody.containsKey("description")) {
                return ApiGatewayResponseUtil.buildErrorResponse(400, "Missing required fields: name, assignedTo, deadline, description");
            }

            // Verify assignedTo user exists
            String assignedTo = (String) requestBody.get("assignedTo");
            User assignedUser = userRepository.getUserById(assignedTo);
            if (assignedUser == null) {
                return ApiGatewayResponseUtil.buildErrorResponse(400, "Assigned user not found");
            }

            // Create task object
            Task task = new Task();
            task.setTaskId(UUID.randomUUID().toString());
            task.setName((String) requestBody.get("name"));
            task.setDescription((String) requestBody.get("description"));
            task.setAssignedTo(assignedTo);
            task.setDeadline((String) requestBody.get("deadline"));
            task.setStatus("open");
            task.setCreatedAt(Instant.now().toString());
            // task.setCreatedBy(userId);

            // Enqueue task for processing
            String taskJson = objectMapper.writeValueAsString(task);
            
            // Add to FIFO queue with content-based deduplication
            String messageGroupId = assignedTo; // Group by assignedTo user
            
            SendMessageRequest sendMessageRequest = SendMessageRequest.builder()
                    .queueUrl(taskQueueUrl)
                    .messageBody(taskJson)
                    .messageGroupId(messageGroupId)
                    .build();
            
            SendMessageResponse sendMessageResponse = sqsClient.sendMessage(sendMessageRequest);
            
            context.getLogger().log("Task queued with message ID: " + sendMessageResponse.messageId());

            // Return success response with the created task
            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("message", "Task created successfully");
            responseBody.put("taskId", task.getTaskId());
            responseBody.put("status", "queued");
            
            return ApiGatewayResponseUtil.buildSuccessResponse(201, responseBody);
            
        } catch (Exception e) {
            context.getLogger().log("Error creating task: " + e.getMessage());
            return ApiGatewayResponseUtil.buildErrorResponse(500, "Error creating task: " + e.getMessage());
        }
    }
}