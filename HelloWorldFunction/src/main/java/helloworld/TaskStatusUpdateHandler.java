package helloworld;

import java.time.Instant;
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
import software.amazon.awssdk.services.sns.model.PublishRequest;

public class TaskStatusUpdateHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final SnsClient snsClient;
    private final ObjectMapper objectMapper;
    private final String taskCompleteTopicArn;

    public TaskStatusUpdateHandler() {
        this.taskRepository = new TaskRepository();
        this.userRepository = new UserRepository();
        this.snsClient = SnsClient.builder().build();
        this.objectMapper = new ObjectMapper();
        this.taskCompleteTopicArn = System.getenv("TASK_COMPLETE_TOPIC_ARN");
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
            
            // Get the task
            Task task = taskRepository.getTask(taskId);
            if (task == null) {
                return ApiGatewayResponseUtil.buildErrorResponse(404, "Task not found");
            }
            
            // // Check if the user is authorized to update the task
            // boolean isAdmin = "admin".equals(currentUser.getRole());
            // boolean isAssignedUser = task.getAssignedTo().equals(userId);
            
            // if (!isAdmin && !isAssignedUser) {
            //     return ApiGatewayResponseUtil.buildErrorResponse(403, "You are not authorized to update this task");
            // }
            
            // Parse the update request
            Map<String, Object> requestBody = objectMapper.readValue(input.getBody(), Map.class);
            
            // Process updates
            boolean statusChanged = false;
            String oldStatus = task.getStatus();
            
            // Update status if provided
            if (requestBody.containsKey("status")) {
                String newStatus = (String) requestBody.get("status");
                
                // // Regular users can only change status to 'completed'
                // if (!isAdmin && !"completed".equals(newStatus)) {
                //     return ApiGatewayResponseUtil.buildErrorResponse(400, "You can only mark tasks as completed");
                // }
                
                // // Check if we're trying to update an expired task
                // if ("expired".equals(oldStatus) && !isAdmin) {
                //     return ApiGatewayResponseUtil.buildErrorResponse(400, "Expired tasks can only be updated by administrators");
                // }
                
                task.setStatus(newStatus);
                statusChanged = true;
                
                // If task is being completed, set completed_at timestamp
                if ("completed".equals(newStatus)) {
                    task.setCompletedAt(Instant.now().toString());
                }
            }
            
            // Update user_comment if provided
            if (requestBody.containsKey("user_comment")) {
                task.setUserComment((String) requestBody.get("user_comment"));
            }
            
            // Save the updated task
            taskRepository.saveTask(task);
            
            // // If status changed to completed, notify admin
            // if (statusChanged && "completed".equals(task.getStatus())) {
            //     notifyTaskComplete(task, currentUser, context);
            // }
            
            // Return success response
            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("message", "Task updated successfully");
            responseBody.put("taskId", task.getTaskId());
            responseBody.put("status", task.getStatus());
            
            return ApiGatewayResponseUtil.buildSuccessResponse(200, responseBody);
            
        } catch (Exception e) {
            context.getLogger().log("Error updating task: " + e.getMessage());
            return ApiGatewayResponseUtil.buildErrorResponse(500, "Error updating task: " + e.getMessage());
        }
    }

    private void notifyTaskComplete(Task task, User user, Context context) {
        try {
            // Get admin users
            User admin = userRepository.getAdminUser();
            
            // Create message for SNS
            Map<String, Object> message = new HashMap<>();
            message.put("type", "TASK_COMPLETE");
            message.put("taskId", task.getTaskId());
            message.put("taskName", task.getName());
            message.put("description", task.getDescription());
            message.put("completedBy", user.getUserId());
            message.put("completedByName", user.getName());
            message.put("completedByEmail", user.getEmail());
            message.put("completedAt", task.getCompletedAt());
            message.put("userComment", task.getUserComment());
            
            // Publish to SNS
            PublishRequest publishRequest = PublishRequest.builder()
                    .topicArn(taskCompleteTopicArn)
                    .message(objectMapper.writeValueAsString(message))
                    .subject("Task Completed: " + task.getName())
                    .build();
            
            snsClient.publish(publishRequest);
            context.getLogger().log("Task complete notification sent for task: " + task.getTaskId());
            
        } catch (Exception e) {
            context.getLogger().log("Error sending task complete notification: " + e.getMessage());
        }
    }
}