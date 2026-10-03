package com.harish.pickles.web.mvc;

import com.harish.pickles.domain.Order;
import com.harish.pickles.domain.OrderStatus;
import com.harish.pickles.exception.BusinessException;
import com.harish.pickles.service.OrderService;
import com.harish.pickles.web.CurrentUser;
import java.util.List;
import java.util.Set;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** What a delivery partner sees after signing in: their own orders, and nothing else. */
@Controller
@RequestMapping("/delivery")
@PreAuthorize("hasRole('DELIVERY_PARTNER')")
public class DeliveryViewController {

    /** Orders still on the road: paid and waiting for pickup, or already out with the courier. */
    private static final Set<OrderStatus> IN_PROGRESS =
            Set.of(OrderStatus.PAID, OrderStatus.PACKED, OrderStatus.SHIPPED);

    private final OrderService orderService;

    public DeliveryViewController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public String dashboard(Model model) {
        List<Order> mine = orderService.partnerOrders(CurrentUser.require());
        model.addAttribute("active", mine.stream().filter(o -> IN_PROGRESS.contains(o.getStatus())).toList());
        model.addAttribute("finished", mine.stream().filter(o -> !IN_PROGRESS.contains(o.getStatus()))
                .limit(30).toList());
        return "delivery/dashboard";
    }

    @PostMapping("/orders/{orderNumber}/status")
    public String updateStatus(@PathVariable String orderNumber, @RequestParam OrderStatus status,
                               RedirectAttributes flash) {
        try {
            orderService.updateStatusAsPartner(orderNumber, CurrentUser.require(), status);
            flash.addFlashAttribute("message", orderNumber + (status == OrderStatus.DELIVERED
                    ? " marked as delivered" : " marked as out for delivery"));
        } catch (BusinessException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/delivery";
    }
}
