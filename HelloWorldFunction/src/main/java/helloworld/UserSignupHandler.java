package helloworld;

import java.util.HashMap;
import java.util.Map;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.CognitoEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

import helloworld.repository.UserRepository;
import software.amazon.awssdk.services.sfn.SfnClient;
import software.amazon.awssdk.services.sfn.model.StartExecutionRequest;

public class UserSignupHandler implements RequestHandler<CognitoEvent, String> {

    private final UserRepository userRepository;
    private final SfnClient sfnClient;
    private final ObjectMapper objectMapper;
    private final String userOnboardingStateMachineArn;

    public UserSignupHandler() {
        this.userRepository = new UserRepository();
        this.sfnClient = SfnClient.builder().build();
        this.objectMapper = new ObjectMapper();
        this.userOnboardingStateMachineArn = System.getenv("USER_ONBOARDING_STATE_MACHINE_ARN");
    }

    @Override
    public String handleRequest(CognitoEvent event, Context context) {
        try {
            context.getLogger().log("Processing Cognito event: " + event.getEventType());
            
            // Extract user details from the Cognito event
            String userId = event.getDatasetRecords().get("userId").toString();
            String email = event.getDatasetRecords().get("email").toString();
            String name = event.getDatasetRecords().get("name").toString();
            String role = event.getDatasetRecords().containsKey("custom:role") 
                ? event.getDatasetRecords().get("custom:role").toString() 
                : "user";
            
            // Create user in our database
            User user = new User();
            user.setUserId(userId);
            user.setEmail(email);
            user.setName(name);
            user.setRole(role);
            
            userRepository.saveUser(user);
            context.getLogger().log("User saved to database: " + userId);
            
            // Start user onboarding state machine
            Map<String, Object> stepFunctionInput = new HashMap<>();
            stepFunctionInput.put("userId", userId);
            stepFunctionInput.put("email", email);
            stepFunctionInput.put("name", name);
            stepFunctionInput.put("role", role);
            
            StartExecutionRequest startExecutionRequest = StartExecutionRequest.builder()
                    .stateMachineArn(userOnboardingStateMachineArn)
                    .input(objectMapper.writeValueAsString(stepFunctionInput))
                    .build();
            
            sfnClient.startExecution(startExecutionRequest);
            context.getLogger().log("Started user onboarding workflow for user: " + userId);
            
            return "Successfully processed user signup";
            
        } catch (Exception e) {
            context.getLogger().log("Error processing user signup: " + e.getMessage());
            return "Error: " + e.getMessage();
        }
    }
}