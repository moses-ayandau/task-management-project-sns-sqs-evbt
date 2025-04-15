package helloworld;

import java.util.HashMap;
import java.util.Map;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

import helloworld.repository.TaskRepository;
import helloworld.repository.UserRepository;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;

public class TaskReassignmentHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final SnsClient snsClient;
    private final ObjectMapper objectMapper;
    private final String taskAssignmentTopicArn;

    public TaskReassignmentHandler() {
        this.taskRepository = new TaskRepository();
        this.userRepository = new UserRepository();
        this.snsClient = SnsClient.builder().build();
        this.objectMapper = new ObjectMapper();
        this.taskAssignmentTopicArn = System.getenv("TASK_ASSIGNMENT_TOPIC_ARN");
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        try {
            // Get the task ID from the path parameters
            String taskId = input.getPathParameters().get("taskId");
            if (taskId == null || taskId.trim().isEmpty()) {
                return ApiGatewayResponseUtil.buildErrorResponse(400, "Task ID is required");
            }
            
            // // Get the authenticated user
            // String userId = CognitoAuthorizer.getUserId(input);
            // User currentUser = userRepository.getUserById(userId);
            
            // if (currentUser == null) {
            //     return ApiGatewayResponseUtil.buildErrorResponse(404, "User not found");
            // }
            
            // // Only administrators can reassign tasks
            // if (!"admin".equals(currentUser.getRole())) {
            //     return ApiGatewayResponseUtil.buildErrorResponse(403, "Only administrators can reassign tasks");
            // }
            
            // Get the task
            Task task = taskRepository.getTask(taskId);
            if (task == null) {
                return ApiGatewayResponseUtil.buildErrorResponse(404, "Task not found");
            }
            
            // Parse the reassignment request
            Map<String, Object> requestBody = objectMapper.readValue(input.getBody(), Map.class);
            
            if (!requestBody.containsKey("assignedTo")) {
                return ApiGatewayResponseUtil.buildErrorResponse(400, "New assignee ID (assignedTo) is required");
            }
            
            String newAssigneeId = (String) requestBody.get("assignedTo");
            String oldAssigneeId = task.getAssignedTo();
            
            // Make sure new assignee exists
            User newAssignee = userRepository.getUserById(newAssigneeId);
            if (newAssignee == null) {
                return ApiGatewayResponseUtil.buildErrorResponse(400, "New assignee not found");
            }
            
            // Update task status if needed (reopen expired tasks)
            if ("expired".equals(task.getStatus())) {
                task.setStatus("open");
            }
            
            // Store previous assignee for reference
            task.setPreviousAssignedTo(oldAssigneeId);
            
            // Update the assignee
            task.setAssignedTo(newAssigneeId);
            
            // Save the updated task
            taskRepository.saveTask(task);
            
            // Notify the new assignee
            notifyNewAssignee(task, newAssignee, context);
            
            // Return success response
            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("message", "Task reassigned successfully");
            responseBody.put("taskId", task.getTaskId());
            responseBody.put("previousAssignedTo", oldAssigneeId);
            responseBody.put("newAssignedTo", newAssigneeId);
            responseBody.put("status", task.getStatus());
            
            return ApiGatewayResponseUtil.buildSuccessResponse(200, responseBody);
            
        } catch (Exception e) {
            context.getLogger().log("Error reassigning task: " + e.getMessage());
            return ApiGatewayResponseUtil.buildErrorResponse(500, "Error reassigning task: " + e.getMessage());
        }
    }
    private void notifyNewAssignee(Task task, User newAssignee, Context context) {
        try {
            // Create message for SNS
            Map<String, Object> message = new HashMap<>();
            message.put("type", "TASK_REASSIGNMENT");
            message.put("taskId", task.getTaskId());
            message.put("taskName", task.getName());
            message.put("description", task.getDescription());
            message.put("deadline", task.getDeadline());
            message.put("assignedTo", newAssignee.getUserId());
            message.put("assignedToEmail", newAssignee.getEmail());
            message.put("assignedToName", newAssignee.getName());
            
            // Create message attributes for filtering
            Map<String, MessageAttributeValue> messageAttributes = new HashMap<>();
            messageAttributes.put("userId", MessageAttributeValue.builder()
                    .dataType("String")
                    .stringValue(newAssignee.getUserId())
                    .build());
            
            // Publish to SNS
            PublishRequest publishRequest = PublishRequest.builder()
                    .topicArn(taskAssignmentTopicArn)
                    .message(objectMapper.writeValueAsString(message))
                    .subject("Task Reassigned to You: " + task.getName())
                    .messageAttributes(messageAttributes)
                    .build();
            
            snsClient.publish(publishRequest);
            context.getLogger().log("Task reassignment notification sent for task: " + task.getTaskId());
            
        } catch (Exception e) {
            context.getLogger().log("Error sending task reassignment notification: " + e.getMessage());
        }
    }
}
