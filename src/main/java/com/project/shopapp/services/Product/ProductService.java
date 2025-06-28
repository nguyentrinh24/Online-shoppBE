package com.project.shopapp.services.Product;

import com.project.shopapp.dtos.ProductDTO;
import com.project.shopapp.dtos.ProductImageDTO;
import com.project.shopapp.exceptions.DataNotFoundException;
import com.project.shopapp.exceptions.InvalidParamException;
import com.project.shopapp.models.Category;
import com.project.shopapp.models.Product;
import com.project.shopapp.models.ProductImage;
import com.project.shopapp.repositories.CategoryRepository;
import com.project.shopapp.repositories.ProductImageRepository;
import com.project.shopapp.repositories.ProductRepository;
import com.project.shopapp.responses.Product.ProductResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProductService implements IProductService {
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductImageRepository productImageRepository;

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

        return productRepository.save(newProduct);
    }

    @Override
    public Product getProductById(long productId) throws Exception {
        Product product = productRepository.getDetailProduct(productId)
                .orElseThrow(() -> new DataNotFoundException(
                        "Không tìm thấy Product với id = " + productId));

        return product;
    }

    //  TÌM NHIỀU SẢN PHẨM THEO DANH SÁCH ID
    @Override
    public List<Product> findProductsByIds(List<Long> productIds) {
        return productRepository.findProductsByIds(productIds);
    }

    @Override
    public Page<ProductResponse> getAllProducts(String keyword,
                                                Long categoryId, PageRequest pageRequest) {
        // Truy vấn DB và map về DTO
        Page<Product> productsPage =
                productRepository.searchProducts(categoryId, keyword, pageRequest);
        Page<ProductResponse> responsePage =
                productsPage.map(ProductResponse::fromProduct);

        return responsePage;
    }

    @Override
    @Transactional
    public Product updateProduct(long id, ProductDTO productDTO) throws Exception {
        // Kiểm tra product tồn tại
        Product existingProduct = productRepository.findById(id)
                .orElseThrow(() -> new DataNotFoundException(
                        "Không tìm thấy Product với id = " + id));

        // Kiểm tra category tồn tại
        Category category = categoryRepository.findById(productDTO.getCategoryId())
                .orElseThrow(() -> new DataNotFoundException(
                        "Không tìm thấy Category với id = " + productDTO.getCategoryId()));

        // Cập nhật
        existingProduct.setName(productDTO.getName());
        existingProduct.setCategory(category);
        existingProduct.setPrice(productDTO.getPrice());
        existingProduct.setStock_quantity(productDTO.getStock_quantity());
        existingProduct.setQuantity(productDTO.getQuantity());
        existingProduct.setDescription(productDTO.getDescription());
        if (productDTO.getThumbnail() != null && !productDTO.getThumbnail().isEmpty()) {
            existingProduct.setThumbnail(productDTO.getThumbnail());
        }
        // Lưu thay đổi
        Product savedProduct = productRepository.save(existingProduct);

        return savedProduct;
    }

    @Override
    @Transactional
    public void deleteProduct(long id) {
        Optional<Product> optional = productRepository.findById(id);
        if (optional.isPresent()) {
            productRepository.delete(optional.get());
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
                .findById(productImageDTO.getProductId())
                .orElseThrow(()->
                        new DataNotFoundException(
                                "Cannot find product with id: "+productImageDTO.getProductId()));
        ProductImage newProductImage = ProductImage.builder()
                .product(existingProduct)
                .imageUrl(productImageDTO.getImageUrl())
                .build();
        //không cho insert quá 5 ảnh cho 1 sản phẩm
        int size = productImageRepository.findByProductId(productId).size();
        if(size >= ProductImage.MAXIMUM_IMAGES_PER_PRODUCT) {
            throw new InvalidParamException(
                    "Number of images must be <= " + ProductImage.MAXIMUM_IMAGES_PER_PRODUCT);
        }
        return productImageRepository.save(newProductImage);
    }

    @Override
    @Transactional
    public void deleteProductImage(Long imageId) throws Exception {
        ProductImage image = productImageRepository.findById(imageId)
                .orElseThrow(() -> new DataNotFoundException("Image not found with id = " + imageId));

        Path imagePath = Paths.get("uploads/" + image.getImageUrl());
        if (Files.exists(imagePath)) {
            Files.delete(imagePath);
        }

        productImageRepository.deleteById(imageId);
    }
    
    @Override
    public List<Product> getFeaturedProducts(int limit) {
        // For now, return the most recent products as featured
        // In a real application, you might have a 'featured' field in the Product model
        PageRequest pageRequest = PageRequest.of(0, limit);
        return productRepository.findTopByOrderByIdDesc(pageRequest);
    }
}
