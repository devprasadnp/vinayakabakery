package com.harish.pickles.web.mvc;

import com.harish.pickles.exception.BusinessException;
import com.harish.pickles.service.CouponService;
import com.harish.pickles.service.InventoryCategoryService;
import com.harish.pickles.service.OrderService;
import com.harish.pickles.service.ProductService;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Everything only a super admin may do: archive or permanently delete products, orders,
 * coupons and categories. (Account management moved to {@link AdminAccountsViewController}.)
 */
@Controller
@RequestMapping("/superadmin")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SuperAdminViewController {

    private final ProductService productService;
    private final OrderService orderService;
    private final CouponService couponService;
    private final InventoryCategoryService categoryService;

    public SuperAdminViewController(ProductService productService,
                                    OrderService orderService, CouponService couponService,
                                    InventoryCategoryService categoryService) {
        this.productService = productService;
        this.orderService = orderService;
        this.couponService = couponService;
        this.categoryService = categoryService;
    }

    // -------------------------------------------------------------- accounts

    // Account screens now live under /admin so admins can reach the parts they are allowed
    // (delivery partners, customer password resets). The old links keep working.

    @GetMapping("/customers")
    public String customers() {
        return "redirect:/admin/customers";
    }

    @GetMapping("/staff")
    public String staff() {
        return "redirect:/admin/staff";
    }

    // --------------------------------------------------------------- archive

    @GetMapping("/archive")
    public String archive(Model model) {
        model.addAttribute("archivedProducts", productService.archived());
        model.addAttribute("archivedOrders", orderService.archivedOrders());
        model.addAttribute("archivedCoupons", couponService.archived());
        model.addAttribute("archivedCategories", categoryService.archived());
        model.addAttribute("activePage", "archive");
        return "superadmin/archive";
    }

    @PostMapping("/products/{id}/archive")
    public String archiveProduct(@PathVariable Long id, RedirectAttributes flash) {
        var product = productService.archive(id);
        flash.addFlashAttribute("message", product.getName()
                + " was archived. Delete it for good from the archive, or restore it any time.");
        return "redirect:/admin/products";
    }

    @PostMapping("/products/{id}/restore")
    public String restoreProduct(@PathVariable Long id, RedirectAttributes flash) {
        var product = productService.restore(id);
        flash.addFlashAttribute("message", product.getName()
                + " is back in the catalogue, hidden until you set it live.");
        return "redirect:/superadmin/archive";
    }

    @PostMapping("/products/{id}/delete")
    public String deleteProduct(@PathVariable Long id, RedirectAttributes flash) {
        try {
            String name = productService.deletePermanently(id);
            flash.addFlashAttribute("message", name + " was permanently deleted");
        } catch (BusinessException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/superadmin/archive";
    }

    @PostMapping("/orders/{orderNumber}/archive")
    public String archiveOrder(@PathVariable String orderNumber, RedirectAttributes flash) {
        orderService.archive(orderNumber);
        flash.addFlashAttribute("message", orderNumber + " was archived");
        return "redirect:/admin/orders";
    }

    @PostMapping("/orders/{orderNumber}/restore")
    public String restoreOrder(@PathVariable String orderNumber, RedirectAttributes flash) {
        orderService.restore(orderNumber);
        flash.addFlashAttribute("message", orderNumber + " was restored");
        return "redirect:/superadmin/archive";
    }

    @PostMapping("/orders/{orderNumber}/delete")
    public String deleteOrder(@PathVariable String orderNumber, RedirectAttributes flash) {
        try {
            orderService.deletePermanently(orderNumber);
            flash.addFlashAttribute("message", orderNumber + " was permanently deleted");
        } catch (BusinessException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/superadmin/archive";
    }

    @PostMapping("/coupons/{id}/archive")
    public String archiveCoupon(@PathVariable Long id, RedirectAttributes flash) {
        var coupon = couponService.archive(id);
        flash.addFlashAttribute("message", coupon.getCode()
                + " was archived. Delete it for good from the archive, or restore it any time.");
        return "redirect:/admin/coupons";
    }

    @PostMapping("/coupons/{id}/restore")
    public String restoreCoupon(@PathVariable Long id, RedirectAttributes flash) {
        var coupon = couponService.restore(id);
        flash.addFlashAttribute("message", coupon.getCode() + " is back and can be edited again from Coupons.");
        return "redirect:/superadmin/archive";
    }

    @PostMapping("/coupons/{id}/delete")
    public String deleteCoupon(@PathVariable Long id, RedirectAttributes flash) {
        try {
            String code = couponService.deletePermanently(id);
            flash.addFlashAttribute("message", code + " was permanently deleted");
        } catch (BusinessException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/superadmin/archive";
    }

    @PostMapping("/categories/{id}/archive")
    public String archiveCategory(@PathVariable Long id, RedirectAttributes flash) {
        try {
            var category = categoryService.archive(id);
            flash.addFlashAttribute("message", category.getName()
                    + " was archived. Delete it for good from the archive, or restore it any time.");
        } catch (BusinessException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/categories";
    }

    @PostMapping("/categories/{id}/restore")
    public String restoreCategory(@PathVariable Long id, RedirectAttributes flash) {
        var category = categoryService.restore(id);
        flash.addFlashAttribute("message", category.getName() + " is back and can be edited again from Categories.");
        return "redirect:/superadmin/archive";
    }

    @PostMapping("/categories/{id}/delete")
    public String deleteCategory(@PathVariable Long id, RedirectAttributes flash) {
        try {
            String name = categoryService.deletePermanently(id);
            flash.addFlashAttribute("message", name + " was permanently deleted");
        } catch (BusinessException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/superadmin/archive";
    }
}
