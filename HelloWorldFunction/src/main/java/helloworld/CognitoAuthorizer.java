package helloworld;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;

import java.util.Map;

public class CognitoAuthorizer {

    public static String getUserId(APIGatewayProxyRequestEvent event) {
        // Extract claims from the request context
        Map<String, Object> claims = extractClaims(event);
        
        if (claims != null && claims.containsKey("sub")) {
            return claims.get("sub").toString();
        }
        
        // If no sub claim found, try to extract from Authorization header
        String token = extractToken(event);
        if (token != null) {
            try {
                DecodedJWT jwt = JWT.decode(token);
                return jwt.getSubject();
            } catch (Exception e) {
                // Invalid token
                return null;
            }
        }
        
        return null;
    }
    
    public static String getUserEmail(APIGatewayProxyRequestEvent event) {
        // Extract claims from the request context
        Map<String, Object> claims = extractClaims(event);
        
        if (claims != null && claims.containsKey("email")) {
            return claims.get("email").toString();
        }
        
        return null;
    }
    
    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractClaims(APIGatewayProxyRequestEvent event) {
        if (event.getRequestContext() != null && 
            event.getRequestContext().getAuthorizer() != null &&
            event.getRequestContext().getAuthorizer().containsKey("claims")) {
            
            return (Map<String, Object>) event.getRequestContext().getAuthorizer().get("claims");
        }
        
        return null;
    }
    
    private static String extractToken(APIGatewayProxyRequestEvent event) {
        if (event.getHeaders() != null && event.getHeaders().containsKey("Authorization")) {
            String authHeader = event.getHeaders().get("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                return authHeader.substring(7);
            }
        }
        return null;
    }
}