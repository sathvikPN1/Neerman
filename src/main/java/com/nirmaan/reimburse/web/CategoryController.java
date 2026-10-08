package com.nirmaan.reimburse.web;

import com.nirmaan.reimburse.category.CategoryForm;
import com.nirmaan.reimburse.category.CategoryService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class CategoryController {

    private final CategoryService categories;

    public CategoryController(CategoryService categories) {
        this.categories = categories;
    }

    /** Everyone can read the rules; only CATEGORY_MANAGE can edit. */
    @GetMapping("/categories")
    public String list(Model model) {
        model.addAttribute("categories", categories.listAll());
        return "categories/list";
    }

    @PostMapping("/categories")
    @PreAuthorize("hasAuthority('CATEGORY_MANAGE')")
    public String save(@Valid @ModelAttribute CategoryForm form, BindingResult errors, RedirectAttributes redirect) {
        if (errors.hasErrors()) {
            redirect.addFlashAttribute("error", "Enter a name and a description.");
        } else {
            categories.save(form);
            redirect.addFlashAttribute("success", "Category saved.");
        }
        return "redirect:/categories";
    }
}
