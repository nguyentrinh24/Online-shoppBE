package com.project.shopapp.services.Product;

import com.project.shopapp.dtos.product.ProductDTO;
import com.project.shopapp.dtos.product.ProductImageDTO;
import com.project.shopapp.exceptions.DataNotFoundException;
import com.project.shopapp.exceptions.InvalidParamException;
import com.project.shopapp.models.Category;
import com.project.shopapp.models.Product;
import com.project.shopapp.models.ProductImage;
import com.project.shopapp.redis.BaseRedis;
import com.project.shopapp.repositories.CategoryRepository;
import com.project.shopapp.repositories.ProductImageRepository;
import com.project.shopapp.repositories.ProductRepository;
import com.project.shopapp.responses.ProductResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class ProductService implements IProductService {
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRedisService productRedisService;
    private final ProductImageRepository productImageRepository;
    private final BaseRedis baseRedis;

    private static final String PRODUCT_KEY_PREFIX = "product:";
    private static final long PRODUCT_CACHE_DURATION = 24; // 24 hours

    @Override
    @Transactional
    public Product createProduct(ProductDTO productDTO) throws DataNotFoundException {
        Category existingCategory = categoryRepository
                .findById(productDTO.getCategoryId())
                .orElseThrow(() ->
                        new DataNotFoundException(
                                "Cannot find category with id: "+productDTO.getCategoryId()));

        Product newProduct = Product.builder()
                .name(productDTO.getName())
                .price(productDTO.getPrice())
                .thumbnail(productDTO.getThumbnail())
                .quantity(productDTO.getQuantity())
                .stock_quantity(productDTO.getStock_quantity())
                .description(productDTO.getDescription())
                .category(existingCategory)
                .build();
        Product savedProduct = productRepository.save(newProduct);
        

        String productKey = PRODUCT_KEY_PREFIX + savedProduct.getId();
        baseRedis.setProductWithExpiration(productKey, savedProduct, PRODUCT_CACHE_DURATION, TimeUnit.HOURS);
        

        productRedisService.clear();
        

        try {
            PageRequest pageRequest = PageRequest.of(0, 10, Sort.by("id").ascending());
            Page<ProductResponse> productsPage = getAllProducts("", 0L, pageRequest);
            if (productsPage != null && !productsPage.getContent().isEmpty()) {
                productRedisService.saveAllProducts(
                    productsPage.getContent(),
                    "",
                    0L,
                    pageRequest
                );
            }
        } catch (Exception e) {
            System.err.println("Error reloading products to Redis: " + e.getMessage());
        }
        
        return savedProduct;
    }

    @Override
    @Transactional
    public Product getProductById(long productId) throws Exception {

        String productKey = PRODUCT_KEY_PREFIX + productId;
        Product cachedProduct = (Product) baseRedis.getProduct(productKey);
        
        if (cachedProduct != null) {
            return cachedProduct;
        }


        Optional<Product> optionalProduct = productRepository.getDetailProduct(productId);
        if(optionalProduct.isPresent()) {
            Product product = optionalProduct.get();
            // Cache the product
            baseRedis.setProductWithExpiration(productKey, product, PRODUCT_CACHE_DURATION, TimeUnit.HOURS);
            return product;
        }
        throw new DataNotFoundException("Cannot find product with id =" + productId);
    }

    @Override
    public List<Product> findProductsByIds(List<Long> productIds) {
        return productRepository.findProductsByIds(productIds);
    }

    @Override
    public Page<ProductResponse> getAllProducts(String keyword,
                                                Long categoryId, PageRequest pageRequest) {
        Page<Product> productsPage;
        productsPage = productRepository.searchProducts(categoryId, keyword, pageRequest);
        return productsPage.map(ProductResponse::fromProduct);
    }

    @Override
    @Transactional
    public Product updateProduct(
            long id,
            ProductDTO productDTO
    ) throws Exception {
        Product existingProduct = getProductById(id);
        if(existingProduct != null) {
            // Chỉ cập nhật category nếu có thay đổi
            if (productDTO.getCategoryId() != null && 
                !productDTO.getCategoryId().equals(existingProduct.getCategory().getId())) {
                Category existingCategory = categoryRepository
                        .findById(productDTO.getCategoryId())
                        .orElseThrow(() ->
                                new DataNotFoundException(
                                        "Cannot find category with id: "+productDTO.getCategoryId()));
                existingProduct.setCategory(existingCategory);
            }

            // Chỉ cập nhật các trường nếu có giá trị mới
            if (productDTO.getName() != null && !productDTO.getName().isEmpty()) {
                existingProduct.setName(productDTO.getName());
            }
            
            if (productDTO.getPrice() != null) {
                existingProduct.setPrice(productDTO.getPrice());
            }
            
            if (productDTO.getStock_quantity() != null) {
                existingProduct.setStock_quantity(productDTO.getStock_quantity());
            }
            
            if (productDTO.getQuantity() != null) {
                existingProduct.setQuantity(productDTO.getQuantity());
            }
            
            if (productDTO.getDescription() != null && !productDTO.getDescription().isEmpty()) {
                existingProduct.setDescription(productDTO.getDescription());
            }
            
            if (productDTO.getThumbnail() != null && !productDTO.getThumbnail().isEmpty()) {
                existingProduct.setThumbnail(productDTO.getThumbnail());
            }

            Product updatedProduct = productRepository.save(existingProduct);
            

            String productKey = PRODUCT_KEY_PREFIX + id;
            baseRedis.setProductWithExpiration(productKey, updatedProduct, PRODUCT_CACHE_DURATION, TimeUnit.HOURS);
            

            productRedisService.clear();
            

            try {
                PageRequest pageRequest = PageRequest.of(0, 10, Sort.by("id").ascending());
                Page<ProductResponse> productsPage = getAllProducts("", 0L, pageRequest);
                if (productsPage != null && !productsPage.getContent().isEmpty()) {
                    productRedisService.saveAllProducts(
                        productsPage.getContent(),
                        "",
                        0L,
                        pageRequest
                    );
                }
            } catch (Exception e) {

                System.err.println("Error reloading products to Redis: " + e.getMessage());
            }
            
            return updatedProduct;
        }
        return null;
    }

    @Override
    @Transactional
    public void deleteProduct(long id) {
        Optional<Product> optionalProduct = productRepository.findById(id);
        if (optionalProduct.isPresent()) {
          
            productRepository.delete(optionalProduct.get());
            
            
            String productKey = PRODUCT_KEY_PREFIX + id;
            baseRedis.deleteProduct(productKey);
            
          
            productRedisService.clear();
            
          
            try {
                PageRequest pageRequest = PageRequest.of(0, 10, Sort.by("id").ascending());
                Page<ProductResponse> productsPage = getAllProducts("", 0L, pageRequest);
                if (productsPage != null && !productsPage.getContent().isEmpty()) {
                    productRedisService.saveAllProducts(
                        productsPage.getContent(),
                        "",
                        0L,
                        pageRequest
                    );
                }
            } catch (Exception e) {
                // Log error
                System.err.println("Error reloading products to Redis: " + e.getMessage());
            }
        }
    }

    @Override
    public boolean existsByName(String name) {
        return productRepository.existsByName(name);
    }

    @Override
    @Transactional
    public ProductImage createProductImage(
            Long productId,
            ProductImageDTO productImageDTO) throws Exception {
        Product existingProduct = productRepository
                .findById(productId)
                .orElseThrow(() ->
                        new DataNotFoundException(
                                "Cannot find product with id: "+productImageDTO.getProductId()));
        ProductImage newProductImage = ProductImage.builder()
                .product(existingProduct)
                .imageUrl(productImageDTO.getImageUrl())
                .build();
        //Ko cho insert quá 5 ảnh cho 1 sản phẩm
        int size = productImageRepository.findByProductId(productId).size();
        if(size >= ProductImage.MAXIMUM_IMAGES_PER_PRODUCT) {
            throw new InvalidParamException(
                    "Number of images must be <= "
                    +ProductImage.MAXIMUM_IMAGES_PER_PRODUCT);
        }
        ProductImage savedImage = productImageRepository.save(newProductImage);
        
        // Update Redis cache
        String productKey = PRODUCT_KEY_PREFIX + productId;
        Product cachedProduct = (Product) baseRedis.getProduct(productKey);
        if (cachedProduct != null) {
            if (cachedProduct.getProductImages() == null) {
                cachedProduct.setProductImages(new ArrayList<>());
            }
            cachedProduct.getProductImages().add(savedImage);
            baseRedis.setProductWithExpiration(productKey, cachedProduct, PRODUCT_CACHE_DURATION, TimeUnit.HOURS);
        }
        
        return savedImage;
    }

    @Override
    public void deleteProductImage(Long imageId) throws Exception {
        ProductImage image = productImageRepository.findById(imageId)
                .orElseThrow(() -> new DataNotFoundException("Image not found with id = " + imageId));

       
        Long productId = image.getProduct().getId();

        // Xóa file
        Path imagePath = Paths.get("uploads/" + image.getImageUrl());
        if (Files.exists(imagePath)) {
            Files.delete(imagePath);
        }

        // Xóa khỏi database
        productImageRepository.deleteById(imageId);

        // Update Redis cache
        String productKey = PRODUCT_KEY_PREFIX + productId;
        Product cachedProduct = (Product) baseRedis.getProduct(productKey);
        if (cachedProduct != null && cachedProduct.getProductImages() != null) {
            cachedProduct.getProductImages().removeIf(img -> img.getId().equals(imageId));
            baseRedis.setProductWithExpiration(productKey, cachedProduct, PRODUCT_CACHE_DURATION, TimeUnit.HOURS);
        }
    }
}
