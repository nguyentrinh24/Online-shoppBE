package com.project.shopapp.services.Category;

import com.project.shopapp.dtos.Categories.CategoryDTO;
import com.project.shopapp.models.Category;
import com.project.shopapp.repositories.CategoryRepository;
import com.project.shopapp.redis.BaseRedis;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class CategoryService implements ICategoryService {
    private final CategoryRepository categoryRepository;
    private final BaseRedis baseRedis;
    
    private static final String CATEGORY_KEY_PREFIX = "category:";
    private static final String CATEGORY_LIST_KEY = "category:list";
    private static final long CATEGORY_CACHE_DURATION = 24; // 24 hours

    @Override
    @Transactional
    public Category createCategory(CategoryDTO categoryDTO) {
        Category newCategory = Category
                .builder()
                .name(categoryDTO.getName())
                .build();
        Category savedCategory = categoryRepository.save(newCategory);
        
        // Cache the new category
        String categoryKey = CATEGORY_KEY_PREFIX + savedCategory.getId();
        baseRedis.setProductWithExpiration(categoryKey, savedCategory, CATEGORY_CACHE_DURATION, TimeUnit.HOURS);
        
        // Clear category list cache
        baseRedis.deleteProduct(CATEGORY_LIST_KEY);
        
        return savedCategory;
    }

    @Override
    public Category getCategoryById(long id) {
        // Try to get from cache first
        String categoryKey = CATEGORY_KEY_PREFIX + id;
        Category cachedCategory = (Category) baseRedis.getProduct(categoryKey);
        
        if (cachedCategory != null) {
            return cachedCategory;
        }
        
        // If not in cache, get from database
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found"));
        
        // Cache the category
        baseRedis.setProductWithExpiration(categoryKey, category, CATEGORY_CACHE_DURATION, TimeUnit.HOURS);
        
        return category;
    }

    @Override
    public List<Category> getAllCategories() {
        // Try to get from cache first
        List<Category> cachedCategories = (List<Category>) baseRedis.getProduct(CATEGORY_LIST_KEY);
        
        if (cachedCategories != null) {
            return cachedCategories;
        }
        
        // If not in cache, get from database
        List<Category> categories = categoryRepository.findAll();
        
        // Cache the category list
        baseRedis.setProductWithExpiration(CATEGORY_LIST_KEY, categories, CATEGORY_CACHE_DURATION, TimeUnit.HOURS);
        
        return categories;
    }

    @Override
    @Transactional
    public Category updateCategory(long categoryId, CategoryDTO categoryDTO) {
        Category existingCategory = getCategoryById(categoryId);
        existingCategory.setName(categoryDTO.getName());
        Category updatedCategory = categoryRepository.save(existingCategory);
        
        // Update cache
        String categoryKey = CATEGORY_KEY_PREFIX + categoryId;
        baseRedis.setProductWithExpiration(categoryKey, updatedCategory, CATEGORY_CACHE_DURATION, TimeUnit.HOURS);
        
        // Clear category list cache
        baseRedis.deleteProduct(CATEGORY_LIST_KEY);
        
        return updatedCategory;
    }

    @Override
    @Transactional
    public void deleteCategory(long id) {
        // Delete from database
        categoryRepository.deleteById(id);
        
        // Delete from cache
        String categoryKey = CATEGORY_KEY_PREFIX + id;
        baseRedis.deleteProduct(categoryKey);
        
        // Clear category list cache
        baseRedis.deleteProduct(CATEGORY_LIST_KEY);
    }
}
