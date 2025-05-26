package com.codewithmosh.store.payments;

import com.codewithmosh.store.entities.Order;
import com.codewithmosh.store.entities.OrderItem;
import com.codewithmosh.store.entities.PaymentStatus;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Service
public class StripePaymentGateway implements PaymentGateway{
    @Value("${websiteUrl}")
    private String websiteUrl;

    @Value("${stripe.webhookSecretKey}")
    private String webhookSecretKey;

    @Override
    public CheckoutSession createCheckoutSession(Order order) {
        try{
            var builder = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setSuccessUrl(websiteUrl + "/checkout-success?orderId=" + order.getId())
                    .setCancelUrl(websiteUrl + "/checkout-cancel.html")
                    .putMetadata("order_id",order.getId().toString())
                    .setPaymentIntentData(
                            SessionCreateParams.PaymentIntentData.builder()
                                    .putMetadata("order_id", order.getId().toString())
                                    .build()
                    );

            order.getItems().forEach(item -> {
                var lineItem = createLineItem(item);
                builder.addLineItem(lineItem);
            });

            var session = Session.create(builder.build());
            return new CheckoutSession(session.getUrl());
        } catch(StripeException ex){
            System.out.println(ex.getMessage());
            throw new PaymentException();
        }
    }

    @Override
    public Optional<PaymentResult> parseWebhookRequest(WebhookRequest request) {
        try {
            var payload = request.getPayload();
            var signature = request.getHeaders().get("stripe-signature");
            System.out.println("Processing webhook with signature: " + signature);

            var event = Webhook.constructEvent(payload, signature, webhookSecretKey);

            System.out.println("Received webhook event: " + event.getType());

            return switch (event.getType()) {
                case "payment_intent.succeeded" -> {
                    try {
                        yield Optional.of(new PaymentResult(extractOrderIdFromPaymentIntent(event), PaymentStatus.PAID));
                    } catch (Exception e) {
                        yield Optional.empty();
                    }
                }
                case "payment_intent.payment_failed" -> {
                    try {
                        yield Optional.of(new PaymentResult(extractOrderIdFromPaymentIntent(event), PaymentStatus.FAILED));
                    } catch (Exception e) {
                        System.out.println("Error processing payment_intent.payment_failed: " + e.getMessage());
                        yield Optional.empty();
                    }
                }
                default -> {
                    System.out.println("Unhandled event type: " + event.getType());
                    yield Optional.empty();
                }
            };
        } catch (SignatureVerificationException e) {
            System.out.println("Signature verification failed: " + e.getMessage());
            throw new PaymentException("Invalid Stripe Signature");
        } catch (Exception e) {
            System.out.println("Error processing webhook: " + e.getMessage());
            throw new PaymentException("Error processing webhook: " + e.getMessage());
        }
    }

    private Long extractOrderId(Event event) {
        var stripeObject = event.getDataObjectDeserializer().getObject().orElseThrow(
                () -> new PaymentException("Could not deserialize Stripe event. Check the SDK and API version.")
        );
        var paymentIntent = (PaymentIntent) stripeObject;
        return Long.valueOf(paymentIntent.getMetadata().get("order_id"));
    }

    private Long extractOrderIdFromPaymentIntent(Event event) {
        try {
            System.out.println("Event type: " + event.getType());

            // Try the standard deserialization first
            var stripeObjectOpt = event.getDataObjectDeserializer().getObject();
            if (stripeObjectOpt.isPresent()) {
                var stripeObject = stripeObjectOpt.get();
                System.out.println("Stripe object class: " + stripeObject.getClass().getName());

                if (stripeObject instanceof PaymentIntent paymentIntent) {
                    var orderId = paymentIntent.getMetadata().get("order_id");
                    System.out.println("Extracted order_id from deserialized PaymentIntent: " + orderId);

                    if (orderId != null && !orderId.isEmpty()) {
                        return Long.valueOf(orderId);
                    }
                }
            }

            // Fallback: Parse directly from the raw JSON
            System.out.println("Deserialization failed, trying direct JSON parsing");
            var eventData = event.getData();
            var objectData = eventData.getObject();

            if (objectData instanceof PaymentIntent paymentIntent) {
                var orderId = paymentIntent.getMetadata().get("order_id");
                System.out.println("Extracted order_id from direct PaymentIntent: " + orderId);

                if (orderId != null && !orderId.isEmpty()) {
                    return Long.valueOf(orderId);
                }
            }

            // Final fallback: Parse the raw JSON manually
            System.out.println("Direct object parsing failed, trying manual JSON parsing");
            var rawJson = event.getData().toJson();
            System.out.println("Raw JSON: " + rawJson);

            // Extract order_id from JSON string
            if (rawJson.contains("\"metadata\"") && rawJson.contains("\"order_id\"")) {
                // Find the order_id value in the JSON
                String orderIdPattern = "\"order_id\"\\s*:\\s*\"([^\"]+)\"";
                Pattern pattern = Pattern.compile(orderIdPattern);
                Matcher matcher = pattern.matcher(rawJson);

                if (matcher.find()) {
                    String orderId = matcher.group(1);
                    System.out.println("Extracted order_id from JSON regex: " + orderId);
                    return Long.valueOf(orderId);
                }
            }

            throw new PaymentException("No order_id found in payment intent");

        } catch (NumberFormatException e) {
            System.out.println("Invalid order ID format: " + e.getMessage());
            throw new PaymentException("Invalid order ID format");
        } catch (Exception e) {
            System.out.println("Error extracting order ID from payment intent: " + e.getMessage());
            e.printStackTrace();
            throw new PaymentException("Could not extract order ID from payment intent: " + e.getMessage());
        }
    }

    private SessionCreateParams.LineItem createLineItem(OrderItem item) {
        return SessionCreateParams.LineItem.builder()
                .setQuantity(Long.valueOf(item.getQuantity()))
                .setPriceData(createPriceData(item))
                .build();
    }

    private SessionCreateParams.LineItem.PriceData createPriceData(OrderItem item) {
        return SessionCreateParams.LineItem.PriceData.builder()
                .setCurrency("usd")
                .setUnitAmountDecimal(item.getUnitPrice().multiply(BigDecimal.valueOf(100)))
                .setProductData(createProductData(item))
                .build();
    }

    private SessionCreateParams.LineItem.PriceData.ProductData createProductData(OrderItem item) {
        return SessionCreateParams.LineItem.PriceData.ProductData.builder()
                .setName(item.getProduct().getName())
                .build();
    }
}
