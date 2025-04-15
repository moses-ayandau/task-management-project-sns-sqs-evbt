package helloworld;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

import java.util.HashMap;
import java.util.Map;

import helloworld.repository.TaskRepository;
import helloworld.repository.UserRepository;

public class TaskAssignmentProcessor implements RequestHandler<SQSEvent, String> {

    private final SnsClient snsClient;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final String taskAssignmentTopicArn;

    public TaskAssignmentProcessor() {
        this.snsClient = SnsClient.builder().build();
        this.taskRepository = new TaskRepository();
        this.userRepository = new UserRepository();
        this.objectMapper = new ObjectMapper();
        this.taskAssignmentTopicArn = System.getenv("TASK_ASSIGNMENT_TOPIC_ARN");
    }

    @Override
    public String handleRequest(SQSEvent sqsEvent, Context context) {
        for (SQSEvent.SQSMessage message : sqsEvent.getRecords()) {
            try {
                // Parse task from the SQS message
                Task task = objectMapper.readValue(message.getBody(), Task.class);
                context.getLogger().log("Processing task assignment for task: " + task.getTaskId());

                // Save the task to DynamoDB if it doesn't exist yet
                if (taskRepository.getTask(task.getTaskId()) == null) {
                    taskRepository.saveTask(task);
                }

                // Get user details
                User assignedUser = userRepository.getUserById(task.getAssignedTo());
                if (assignedUser == null) {
                    context.getLogger().log("WARNING: User not found: " + task.getAssignedTo());
                    continue;
                }

                // Send notification to the assigned user
                sendTaskAssignmentNotification(task, assignedUser, context);

            } catch (Exception e) {
                context.getLogger().log("Error processing SQS message: " + e.getMessage());
                return "Error: " + e.getMessage();
            }
        }
        return "Successfully processed task assignments";
    }

    private void sendTaskAssignmentNotification(Task task, User assignedUser, Context context) {
        try {
            // Create a message for the assigned user
            Map<String, String> messageAttributes = new HashMap<>();
            messageAttributes.put("userId", assignedUser.getUserId());
            messageAttributes.put("email", assignedUser.getEmail());

            Map<String, Object> message = new HashMap<>();
            message.put("type", "TASK_ASSIGNMENT");
            message.put("taskId", task.getTaskId());
            message.put("taskName", task.getName());
            message.put("description", task.getDescription());
            message.put("deadline", task.getDeadline());
            message.put("assignedTo", assignedUser.getUserId());
            message.put("assignedToEmail", assignedUser.getEmail());
            message.put("assignedToName", assignedUser.getName());

            // Convert message to JSON
            String messageBody = objectMapper.writeValueAsString(message);

            // Define message attributes for filtering
            Map<String, software.amazon.awssdk.services.sns.model.MessageAttributeValue> snsMessageAttributes = new HashMap<>();
            snsMessageAttributes.put("userId", software.amazon.awssdk.services.sns.model.MessageAttributeValue.builder()
                    .dataType("String")
                    .stringValue(assignedUser.getUserId())
                    .build());

            // Publish to SNS
            PublishRequest publishRequest = PublishRequest.builder()
                    .topicArn(taskAssignmentTopicArn)
                    .message(messageBody)
                    .subject("New Task Assignment: " + task.getName())
                    .messageAttributes(snsMessageAttributes)
                    .build();

            PublishResponse publishResponse = snsClient.publish(publishRequest);
            context.getLogger().log("Task assignment notification sent with message ID: " + publishResponse.messageId());

        } catch (Exception e) {
            context.getLogger().log("Error sending task assignment notification: " + e.getMessage());
            throw new RuntimeException("Failed to send task assignment notification", e);
        }
    }
}