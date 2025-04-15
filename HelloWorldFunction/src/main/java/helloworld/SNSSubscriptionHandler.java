package helloworld;

import java.util.HashMap;
import java.util.Map;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.SubscribeRequest;
import software.amazon.awssdk.services.sns.model.SubscribeResponse;

public class SNSSubscriptionHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private final SnsClient snsClient;
    private final ObjectMapper objectMapper;

    public SNSSubscriptionHandler() {
        this.snsClient = SnsClient.builder().build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            context.getLogger().log("Received event: " + event.toString());
            
            String action = (String) event.get("action");
            String topicArn = (String) event.get("topicArn");
            String protocol = (String) event.get("protocol");
            String endpoint = (String) event.get("endpoint");

            if ("subscribe".equals(action)) {
                SubscribeRequest subscribeRequest = SubscribeRequest.builder()
                    .topicArn(topicArn)
                    .protocol(protocol)
                    .endpoint(endpoint)
                    .build();

                SubscribeResponse subscribeResponse = snsClient.subscribe(subscribeRequest);
                context.getLogger().log("Subscription ARN: " + subscribeResponse.subscriptionArn());

                response.put("statusCode", 200);
                response.put("body", Map.of(
                    "subscriptionArn", subscribeResponse.subscriptionArn(),
                    "message", "Successfully subscribed"
                ));
            } else {
                throw new IllegalArgumentException("Invalid action: " + action);
            }
            
        } catch (Exception e) {
            context.getLogger().log("Error processing subscription: " + e.getMessage());
            response.put("statusCode", 500);
            response.put("body", Map.of(
                "error", e.getMessage(),
                "message", "Failed to process subscription"
            ));
        }
        
        return response;
    }
}