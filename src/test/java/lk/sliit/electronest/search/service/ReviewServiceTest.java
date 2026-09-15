package lk.sliit.electronest.search.service;

import lk.sliit.electronest.catalog.model.Product;
import lk.sliit.electronest.catalog.service.ProductService;
import lk.sliit.electronest.common.model.Role;
import lk.sliit.electronest.common.model.User;
import lk.sliit.electronest.order.model.Order;
import lk.sliit.electronest.order.model.OrderLineItem;
import lk.sliit.electronest.order.model.OrderStatus;
import lk.sliit.electronest.order.repository.OrderRepository;
import lk.sliit.electronest.search.dto.ReviewRequest;
import lk.sliit.electronest.search.model.Review;
import lk.sliit.electronest.search.repository.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ProductService productService;

    @Mock
    private OrderRepository orderRepository;

    private ReviewService reviewService;
    private User customer;

    @BeforeEach
    void setUp() {
        reviewService = new ReviewService(
                reviewRepository,
                productService,
                orderRepository
        );

        customer = new User();
        customer.setId(7L);
        customer.setRole(Role.CUSTOMER);
    }

    @Test
    void deliveredCustomerCanCreateVerifiedReview() {
        when(productService.getProductById(11L)).thenReturn(new Product());
        when(reviewRepository.findByCustomerIdAndProductId(7L, 11L))
                .thenReturn(Optional.empty());
        when(orderRepository.findByCustomer_Id(7L))
                .thenReturn(List.of(deliveredOrder(11L)));
        when(reviewRepository.save(any(Review.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Review review = reviewService.createReview(
                11L,
                new ReviewRequest(5, "Excellent", null),
                customer
        );

        assertTrue(review.isVerified());
        verify(reviewRepository).save(review);
    }

    @Test
    void customerWithoutDeliveredPurchaseCannotReview() {
        when(productService.getProductById(11L)).thenReturn(new Product());
        when(reviewRepository.findByCustomerIdAndProductId(7L, 11L))
                .thenReturn(Optional.empty());
        when(orderRepository.findByCustomer_Id(7L)).thenReturn(List.of());

        assertThrows(
                IllegalStateException.class,
                () -> reviewService.createReview(
                        11L,
                        new ReviewRequest(4, "Too early", null),
                        customer
                )
        );

        verify(reviewRepository, never()).save(any(Review.class));
    }

    private Order deliveredOrder(Long productId) {
        Order order = new Order();
        order.setStatus(OrderStatus.DELIVERED);

        OrderLineItem item = new OrderLineItem();
        item.setProductId(productId);
        order.addLineItem(item);

        return order;
    }
}
