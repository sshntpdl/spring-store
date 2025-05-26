package com.codewithmosh.store.payments;

import com.codewithmosh.store.entities.Order;
import com.codewithmosh.store.exceptions.CartEmptyException;
import com.codewithmosh.store.exceptions.CartNotFoundException;
import com.codewithmosh.store.repositories.CartRepository;
import com.codewithmosh.store.repositories.OrderRepository;
import com.codewithmosh.store.services.AuthService;
import com.codewithmosh.store.services.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class CheckoutService {
    private final CartRepository cartRepository;
    private final OrderRepository orderRepository;
    private final AuthService authService;
    private final CartService cartService;
    private final PaymentGateway paymentGateway;



    @Transactional
    public CheckoutResponse checkout(CheckoutRequest request){
        var cart = cartRepository.getCartWithItems(request.getCartId()).orElse(null);
        if (cart == null) {
            throw new CartNotFoundException();
        }

        if(cart.isEmpty()) {
            throw new CartEmptyException();
        }

        var order = Order.fromCart(cart, authService.getCurrentUser());
        orderRepository.save(order);

    try {
        var session = paymentGateway.createCheckoutSession(order);

        cartService.clearCart(cart.getId());

        return new CheckoutResponse(order.getId(), session.getCheckoutUrl());
    }catch(PaymentException e) {
        orderRepository.delete(order);
        throw e;
    }
    }

//    public void handleWebhookEvent(WebhookRequest request) {
//        paymentGateway.parseWebhookRequest(request)
//                .ifPresent(paymentResult -> {
//                    var order = orderRepository.findById(paymentResult.getOrderId()).orElseThrow();
//                    order.setStatus(paymentResult.getPaymentStatus());
//                    orderRepository.save(order);
//                });
//    }

    @Transactional
    public void handleWebhookEvent(WebhookRequest request) {
        System.out.println("Processing webhook event in CheckoutService");

        try {
            var paymentResultOpt = paymentGateway.parseWebhookRequest(request);

            if (paymentResultOpt.isPresent()) {
                var paymentResult = paymentResultOpt.get();
                System.out.println("Payment result - Order ID: " + paymentResult.getOrderId() +
                        ", Status: " + paymentResult.getPaymentStatus());

                var orderOpt = orderRepository.findById(paymentResult.getOrderId());
                if (orderOpt.isPresent()) {
                    var order = orderOpt.get();
                    var previousStatus = order.getStatus();

                    order.setStatus(paymentResult.getPaymentStatus());
                    var savedOrder = orderRepository.save(order);

                    System.out.println("Order " + order.getId() + " status updated from " +
                            previousStatus + " to " + savedOrder.getStatus());
                } else {
                    System.out.println("Order not found with ID: " + paymentResult.getOrderId());
                }
            } else {
                System.out.println("No payment result returned from webhook parsing");
            }
        } catch (Exception e) {
            System.out.println("Error handling webhook event: " + e.getMessage());
            throw e; // Re-throw to ensure transaction is rolled back
        }
    }
}
