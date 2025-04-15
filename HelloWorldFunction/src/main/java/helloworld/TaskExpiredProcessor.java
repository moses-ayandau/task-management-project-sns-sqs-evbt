package helloworld;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.services.sfn.SfnClient;
import software.amazon.awssdk.services.sfn.model.StartExecutionRequest;

import java.util.HashMap;
import java.util.Map;

import helloworld.repository.TaskRepository;
import helloworld.repository.UserRepository;

public class TaskExpiredProcessor implements RequestHandler<SQSEvent, String> {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final SfnClient sfnClient;
    private final ObjectMapper objectMapper;
    private final String taskExpiredStateMachineArn;

    public TaskExpiredProcessor() {
        this.taskRepository = new TaskRepository();
        this.userRepository = new UserRepository();
        this.sfnClient = SfnClient.builder().build();
        this.objectMapper = new ObjectMapper();
        this.taskExpiredStateMachineArn = System.getenv("TASK_EXPIRED_STATE_MACHINE_ARN");
    }

    @Override
    public String handleRequest(SQSEvent sqsEvent, Context context) {
        for (SQSEvent.SQSMessage message : sqsEvent.getRecords()) {
            try {
                // Parse the message
                Map<String, Object> messageBody = objectMapper.readValue(message.getBody(), Map.class);
                String taskId = (String) messageBody.get("taskId");
                
                // Get the task
                Task task = taskRepository.getTask(taskId);
                if (task == null) {
                    context.getLogger().log("Task not found: " + taskId);
                    continue;
                }
                
                // Get assigned user
                User assignedUser = userRepository.getUserById(task.getAssignedTo());
                if (assignedUser == null) {
                    context.getLogger().log("User not found for task: " + taskId);
                    continue;
                }

                // Get admin users for notification
                User admin = userRepository.getAdminUser();
                
                // Prepare input for Step Function
                Map<String, Object> stepFunctionInput = new HashMap<>();
                stepFunctionInput.put("taskId", task.getTaskId());
                stepFunctionInput.put("taskName", task.getName());
                stepFunctionInput.put("description", task.getDescription());
                stepFunctionInput.put("assignedUserId", assignedUser.getUserId());
                stepFunctionInput.put("assignedUserEmail", assignedUser.getEmail());
                stepFunctionInput.put("assignedUserName", assignedUser.getName());
                stepFunctionInput.put("adminEmail", admin.getEmail());
                stepFunctionInput.put("adminUserId", admin.getUserId());
                
                // Start Step Function execution
                StartExecutionRequest startExecutionRequest = StartExecutionRequest.builder()
                        .stateMachineArn(taskExpiredStateMachineArn)
                        .input(objectMapper.writeValueAsString(stepFunctionInput))
                        .build();
                
                sfnClient.startExecution(startExecutionRequest);
                
                context.getLogger().log("Started task expired workflow for task: " + taskId);
                
            } catch (Exception e) {
                context.getLogger().log("Error processing expired task: " + e.getMessage());
                return "Error: " + e.getMessage();
            }
        }
        
        return "Successfully processed expired tasks";
    }
}