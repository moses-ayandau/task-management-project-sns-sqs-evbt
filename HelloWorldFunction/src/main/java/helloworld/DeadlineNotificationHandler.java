package helloworld;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import helloworld.repository.TaskRepository;
import helloworld.repository.UserRepository;

public class DeadlineNotificationHandler implements RequestHandler<Map<String, Object>, String> {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final SnsClient snsClient;
    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    private final String taskDeadlineTopicArn;
    private final String expiredTasksQueueUrl;

    public DeadlineNotificationHandler() {
        this.taskRepository = new TaskRepository();
        this.userRepository = new UserRepository();
        this.snsClient = SnsClient.builder().build();
        this.sqsClient = SqsClient.builder().build();
        this.objectMapper = new ObjectMapper();
        this.taskDeadlineTopicArn = System.getenv("TASK_DEADLINE_TOPIC_ARN");
        this.expiredTasksQueueUrl = System.getenv("EXPIRED_TASKS_QUEUE_URL");
    }

    @Override
    public String handleRequest(Map<String, Object> input, Context context) {
        context.getLogger().log("Checking task deadlines...");
        
        try {
            // Get all open tasks
            List<Task> tasks = taskRepository.getAllTasks();
            Instant now = Instant.now();
            
            for (Task task : tasks) {
                // Skip completed or expired tasks
                if (!"open".equals(task.getStatus())) {
                    continue;
                }
                
                Instant deadline = Instant.parse(task.getDeadline());
                
                // Check if the task deadline is within 1 hour or has passed
                if (deadline.isBefore(now)) {
                    // Task has expired
                    sendTaskToExpiredQueue(task, context);
                } else if (deadline.minus(1, ChronoUnit.HOURS).isBefore(now)) {
                    // Task is due within 1 hour, send deadline notification
                    sendDeadlineNotification(task, context);
                }
            }
            
            return "Successfully processed task deadlines";
            
        } catch (Exception e) {
            context.getLogger().log("Error processing task deadlines: " + e.getMessage());
            return "Error: " + e.getMessage();
        }
    }

    private void sendDeadlineNotification(Task task, Context context) {
        try {
            User assignedUser = userRepository.getUserById(task.getAssignedTo());
            if (assignedUser == null) {
                context.getLogger().log("WARNING: User not found for task: " + task.getTaskId());
                return;
            }
            
            // Create message for SNS
            Map<String, Object> message = new HashMap<>();
            message.put("type", "TASK_DEADLINE");
            message.put("taskId", task.getTaskId());
            message.put("taskName", task.getName());
            message.put("description", task.getDescription());
            message.put("deadline", task.getDeadline());
            message.put("assignedTo", assignedUser.getUserId());
            message.put("assignedToEmail", assignedUser.getEmail());
            message.put("assignedToName", assignedUser.getName());
            
            // Create message attributes for filtering
            Map<String, MessageAttributeValue> messageAttributes = new HashMap<>();
            messageAttributes.put("userId", MessageAttributeValue.builder()
                    .dataType("String")
                    .stringValue(assignedUser.getUserId())
                    .build());
            
            // Publish to SNS
            PublishRequest publishRequest = PublishRequest.builder()
                    .topicArn(taskDeadlineTopicArn)
                    .message(objectMapper.writeValueAsString(message))
                    .subject("URGENT: Task Deadline Approaching - " + task.getName())
                    .messageAttributes(messageAttributes)
                    .build();
            
            snsClient.publish(publishRequest);
            context.getLogger().log("Deadline notification sent for task: " + task.getTaskId());
            
        } catch (Exception e) {
            context.getLogger().log("Error sending deadline notification: " + e.getMessage());
        }
    }
    
    private void sendTaskToExpiredQueue(Task task, Context context) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("taskId", task.getTaskId());
            message.put("action", "EXPIRE_TASK");
            
            SendMessageRequest sendMessageRequest = SendMessageRequest.builder()
                    .queueUrl(expiredTasksQueueUrl)
                    .messageBody(objectMapper.writeValueAsString(message))
                    .build();
            
            sqsClient.sendMessage(sendMessageRequest);
            context.getLogger().log("Task sent to expired queue: " + task.getTaskId());
            
        } catch (Exception e) {
            context.getLogger().log("Error sending task to expired queue: " + e.getMessage());
        }
    }
}