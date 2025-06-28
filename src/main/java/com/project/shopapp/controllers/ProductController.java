package com.project.shopapp.controllers;

import com.github.javafaker.Faker;
import com.project.shopapp.components.LocalizationUtils;
import com.project.shopapp.dtos.*;
import com.project.shopapp.models.Category;
import com.project.shopapp.models.Product;
import com.project.shopapp.models.ProductImage;
import com.project.shopapp.responses.Product.ProductListResponse;
import com.project.shopapp.responses.Product.ProductResponse;
import com.project.shopapp.services.Category.CategoryService;
import com.project.shopapp.services.Product.IProductService;
import com.project.shopapp.utils.MessageKeys;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.NumberFormat;
import java.util.*;

@RestController
@RequestMapping("${api.prefix}/products")
@RequiredArgsConstructor
public class ProductController {
    private static final Logger logger = LoggerFactory.getLogger(ProductController.class);
    private final IProductService productService;
    private final LocalizationUtils localizationUtils;
    private final CategoryService categoryService;

    @PostMapping("")
    @Transactional
    //POST http://localhost:8088/v1/api/products
    public ResponseEntity<?> createProduct(
            @Valid @RequestBody ProductDTO productDTO,
            BindingResult result
    ) {
        try {
            if (result.hasErrors()) {
                List<String> errorMessages = new ArrayList<>();
                for (FieldError error : result.getFieldErrors()) {
                    errorMessages.add(error.getDefaultMessage());
                }
                return ResponseEntity.badRequest().body(errorMessages);
            }

            Product newProduct = productService.createProduct(productDTO);
            return ResponseEntity.ok(newProduct);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }


    @GetMapping("")
    public ResponseEntity<ProductListResponse> getProducts(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "0", name = "category_id") Long categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int limit
    ) {

        PageRequest pageRequest = PageRequest.of(
                page, limit,
                Sort.by("id").ascending()
        );

        Page<ProductResponse> pageResult = productService.getAllProducts(keyword, categoryId, pageRequest);
        List<ProductResponse> products = pageResult.getContent();
        int totalPages = pageResult.getTotalPages();

        ProductListResponse response = ProductListResponse.builder()
                .products(products)
                .totalPages(totalPages)
                .build();
        return ResponseEntity.ok(response);
    }

    //http://localhost:8088/api/v1/products/6
    @GetMapping("/{id}")
    public ResponseEntity<?> getProductById(
            @PathVariable("id") Long productId
    ) {
        try {
            Product existingProduct = productService.getProductById(productId);
            return ResponseEntity.ok(ProductResponse.fromProduct(existingProduct));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }

    }

    @GetMapping("/by-ids")
    public ResponseEntity<?> getProductsByIds(@RequestParam("ids") String ids) {
        //eg: 1,3,5,7
        try {
            // Tách chuỗi ids thành một mảng các số nguyên
            List<Long> productIds = new ArrayList<>();
            for (String idStr : ids.split(",")) {
                productIds.add(Long.parseLong(idStr));
            }

            List<Product> products = productService.findProductsByIds(productIds);
            return ResponseEntity.ok(products);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<String> deleteProduct(@PathVariable long id) {
        try {
            productService.deleteProduct(id);
            return ResponseEntity.ok(String.format("Product with id = %d deleted successfully", id));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    //update a product
    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<?> updateProduct(
            @PathVariable long id,
            @RequestBody ProductDTO productDTO) {
        try {
            Product updatedProduct = productService.updateProduct(id, productDTO);
            return ResponseEntity.ok(updatedProduct);
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/images/{id}")
    @Transactional
    public ResponseEntity<?> deleteProductImage(@PathVariable("id") Long imageId) {
        try {
            productService.deleteProductImage(imageId); // Gọi service xử lý xóa ảnh
            return ResponseEntity.ok("Image deleted successfully.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }


    @PostMapping(value = "uploads/{id}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    //POST http://localhost:8088/v1/api/products
    public ResponseEntity<?> uploadImages(
            @PathVariable("id") Long productId,
            @ModelAttribute("files") List<MultipartFile> files
    ){
        try {
            Product existingProduct = productService.getProductById(productId);
            files = files == null ? new ArrayList<MultipartFile>() : files;
            if(files.size() > ProductImage.MAXIMUM_IMAGES_PER_PRODUCT) {
                return ResponseEntity.badRequest().body(localizationUtils
                        .getLocalizedMessage(MessageKeys.UPLOAD_IMAGES_MAX_5));
            }
            List<ProductImage> productImages = new ArrayList<>();
            for (MultipartFile file : files) {
                if(file.getSize() == 0) {
                    continue;
                }
                // Kiểm tra kích thước file và định dạng
                if(file.getSize() > 10 * 1024 * 1024) { // > 10MB
                    return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                            .body(localizationUtils.getLocalizedMessage(MessageKeys.UPLOAD_IMAGES_FILE_LARGE));
                }
                String contentType = file.getContentType();
                if(contentType == null || !contentType.startsWith("image/")) {
                    return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                            .body(localizationUtils.getLocalizedMessage(MessageKeys.UPLOAD_IMAGES_FILE_MUST_BE_IMAGE));
                }
                // Lưu file và cập nhật thumbnail trong database
                String filename = storeFile(file); // Thay đổi từ storeFiles thành storeFile
                //lưu vào đối tượng product trong DB
                ProductImage productImage = productService.createProductImage(
                        existingProduct.getId(),
                        ProductImageDTO.builder()
                                .imageUrl(filename)
                                .productId(existingProduct.getId())
                                .build()
                );
                productImages.add(productImage);
            }
            return ResponseEntity.ok().body(productImages);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    private String storeFile(MultipartFile file) throws IOException {
        if (!isImageFile(file) || file.getOriginalFilename() == null) {
            throw new IOException("Invalid image format");
        }
        String filename = StringUtils.cleanPath(file.getOriginalFilename());
        // Thêm UUID vào trước tên file để tránh trùng lặp
        String uniqueFilename = UUID.randomUUID().toString() + "_" + filename;
        // Đường dẫn đến thư mục mà bạn muốn lưu file
        java.nio.file.Path uploadDir = Paths.get("uploads");
        // Kiểm tra và tạo thư mục nếu không tồn tại
        if (!Files.exists(uploadDir)) {
            Files.createDirectories(uploadDir);
        }
        // Đường dẫn đầy đủ đến file
        java.nio.file.Path destination = Paths.get(uploadDir.toString(), uniqueFilename);
        // Sao chép file vào thư mục đích
        Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
        return uniqueFilename;
    }

    private boolean isImageFile(MultipartFile file) {
        String contentType = file.getContentType();
        return contentType != null && contentType.startsWith("image/");
    }

    @GetMapping("/images/{imageName}")
    public ResponseEntity<?> viewImage(@PathVariable String imageName) {
        try {
            java.nio.file.Path imagePath = Paths.get("uploads/" + imageName);
            UrlResource resource = new UrlResource(imagePath.toUri());

            if (resource.exists()) {
                return ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_JPEG)
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }


    // fake date
//    @PostMapping("/generate-fake")
//    @Transactional
//    public ResponseEntity<?> generateFakeProducts(@RequestParam(defaultValue = "") int count) {
//        try {
//            Faker faker = new Faker(new Locale("vi"));
//            Random random = new Random();
//            List<Product> createdProducts = new ArrayList<>();
//            NumberFormat vnFormat = NumberFormat.getInstance(new Locale("vi", "VN"));
//
//            // Dùng tên file ảnh thực tế đã upload (UUID_prefix)
//            Map<String, List<String>> imageMap = Map.of(
//                    "điện thoại", List.of(
//                            "d8f6ac61-9fc9-48f6-a428-ec83263f553e_iphone-16-pro-max-titan-sa-mac-1-638638962337813406-750x500.jpg"
//                    ),
//                    "máy tính", List.of(
//                            "1b524892-8450-4557-a2b3-ae702a3d4c8e_macbook-air-13-inch-m4-11-638769622719537641-750x500.jpg"
//
//                    ),
//                    "màn hình", List.of(
//                            "18964e46-1f3c-4743-8111-4dc22faad8ef_663a91e5-f169-4a3e-9d3f-152ec5ec6f12_asus-lcd-proart-pa247cv-238-inch-full-hd-1-750x500.jpg"
//                    ),
//                    "smartwatch", List.of(
//                            "b886dac5-1f7e-4417-b561-9088f051ac90_apple-watch-s10-day-milan-nau-1-638646969639246948-750x500.jpg"
//                    ),
//                    "phụ kiện", List.of(
//                            "be85d72c-52a7-4d21-a4c6-9afe34dbe7fd_chuot-khong-day-logitech-silent-signature-m650-size-m-den-4-638620837131032134-750x500.jpg"
//                    ),
//                    "âm thanh", List.of(
//                            "4150487f-7c27-41c5-bc5d-a61f945e1ad3_loa-bluetooth-harman-kardon-soundsticks-4-1-750x500.jpg"
//                    ),
//                    "đồng hồ", List.of(
//                            "b886dac5-1f7e-4417-b561-9088f051ac90_apple-watch-s10-day-milan-nau-1-638646969639246948-750x500.jpg"
//                    )
//            );
//
//
//            // Map mô tả chi tiết sản phẩm cho từng category
//            Map<String, List<String>> descriptionMap = Map.of(
//                    "điện thoại", List.of(
//                            "Điện thoại thông minh với màn hình sắc nét và hiệu suất mạnh mẽ. Hỗ trợ 5G và tối ưu cho mọi tác vụ.",
//                            "Máy điện thoại cao cấp với camera tuyệt vời và pin dài, thiết kế sang trọng và màn hình OLED.",
//                            "Sản phẩm điện thoại được trang bị vi xử lý mạnh mẽ, thiết kế mỏng nhẹ, dễ dàng sử dụng cho mọi nhu cầu."
//                    ),
//                    "máy tính", List.of(
//                            "Máy tính xách tay với cấu hình mạnh mẽ, thiết kế siêu mỏng, dễ dàng mang theo. Phù hợp cho công việc và giải trí.",
//                            "Máy tính với màn hình sắc nét, vi xử lý hiện đại, cung cấp hiệu suất tối đa cho các tác vụ đồ họa và lập trình.",
//                            "Máy tính mạnh mẽ với dung lượng bộ nhớ lớn, màn hình chống chói, đảm bảo công việc không bị gián đoạn."
//                    ),
//                    "màn hình", List.of(
//                            "Màn hình chất lượng cao với độ phân giải tuyệt vời, màu sắc trung thực và thời gian phản hồi nhanh.",
//                            "Màn hình 4K sắc nét với tần số quét cao, hoàn hảo cho cả làm việc và giải trí chơi game.",
//                            "Màn hình rộng với khả năng hiển thị màu sắc chính xác, lý tưởng cho công việc đồ họa chuyên nghiệp."
//                    ),
//                    "smartwatch", List.of(
//                            "Đồng hồ thông minh với các tính năng theo dõi sức khỏe và thể dục, màn hình sắc nét và khả năng kết nối tốt.",
//                            "Đồng hồ thông minh đa chức năng với màn hình cảm ứng, theo dõi nhịp tim, và nhiều ứng dụng hỗ trợ.",
//                            "Đồng hồ thông minh thời trang với các tính năng theo dõi sức khỏe, kết nối Bluetooth và thiết kế tiện dụng."
//                    ),
//                    "phụ kiện", List.of(
//                            "Phụ kiện tiện ích hỗ trợ thiết bị điện tử của bạn, từ sạc nhanh đến các bộ chuyển đổi thông minh.",
//                            "Các loại phụ kiện thiết kế thông minh, hỗ trợ tăng cường hiệu suất cho các thiết bị điện tử.",
//                            "Phụ kiện cao cấp với chất liệu bền bỉ, phù hợp với các thiết bị công nghệ hiện đại."
//                    ),
//                    "âm thanh", List.of(
//                            "Loa Bluetooth với âm thanh sống động, dễ dàng kết nối và di chuyển, phù hợp cho mọi không gian.",
//                            "Tai nghe không dây với âm thanh chất lượng cao và khả năng chống ồn hiệu quả.",
//                            "Hệ thống âm thanh đa dạng, cho âm thanh rõ ràng và mạnh mẽ, lý tưởng cho giải trí và làm việc."
//                    ),
//                    "đồng hồ", List.of(
//                            "Đồng hồ cổ điển với thiết kế sang trọng, phù hợp cho các sự kiện đặc biệt.",
//                            "Đồng hồ thể thao mạnh mẽ, với khả năng chống nước và đo nhịp tim chính xác.",
//                            "Đồng hồ thời trang với mặt kính chịu lực, dễ dàng kết hợp với mọi trang phục."
//                    )
//            );
//            List<Category> categories = categoryService.getAllCategories();
//            if (categories.isEmpty()) {
//                return ResponseEntity.badRequest().body("Không có category nào trong DB.");
//            }
//
//
//            for (int i = 0; i < count; i++) {
//                Category category = categories.get(random.nextInt(categories.size()));
//                String categoryKey = category.getName().toLowerCase().trim();
//                // Lấy mô tả dựa trên categoryKey (phải ở trong vòng lặp)
//                List<String> categoryDescriptions = descriptionMap.getOrDefault(categoryKey,
//                        List.of("Sản phẩm " + faker.number().digits(4) + " với nhiều tính năng nổi bật."));
//
//                // Lấy một mô tả ngẫu nhiên cho sản phẩm
//                String productDescription = categoryDescriptions.get(random.nextInt(categoryDescriptions.size()));
//
//                // Thêm thông tin bổ sung từ Faker
//                productDescription += " Thêm vào đó, sản phẩm này có các tính năng như " +
//                        faker.commerce().productName() + ", " + faker.commerce().color() + " sắc nét và dễ sử dụng.";
//
//                List<String> images = imageMap.getOrDefault(categoryKey, List.of("default.jpg"));
//
//                String name = generateProductName(categoryKey, faker, random);
//                long rawPrice = faker.number().numberBetween(1_000_000, 50_000_000);
//                String formattedPrice = vnFormat.format(rawPrice);
//                String thumbnail = images.get(random.nextInt(images.size()));
//
//
//                // Tạo product DTO
//                ProductDTO dto = ProductDTO.builder()
//                        .name(name)
//                        .price((float) rawPrice)
//                        .description(productDescription)
//                        .thumbnail(thumbnail)
//                        .quantity(faker.number().numberBetween(10, 200))
//                        .stock_quantity(faker.number().numberBetween(10, 500))
//                        .categoryId(category.getId())
//                        .build();
//
//                // Tạo product
//                Product product = productService.createProduct(dto);
//
//                // Thêm 3–5 ảnh chi tiết
//                int imageCount = random.nextInt(3) + 3; // từ 3 -> 5 ảnh
//                for (int j = 0; j < imageCount; j++) {
//                    String imageFile = images.get(j % images.size());
//                    ProductImageDTO imageDTO = ProductImageDTO.builder()
//                            .productId(product.getId())
//                            .imageUrl(imageFile)
//                            .build();
//                    productService.createProductImage(product.getId(), imageDTO);
//                }
//
//                createdProducts.add(product);
//
//                if (i % 1000 == 0 && i != 0) {
//                    System.out.println("Đã fake được: " + i + " sản phẩm...");
//                }
//            }
//
//            return ResponseEntity.ok("Đã tạo " + createdProducts.size() + " sản phẩm có ảnh thật, dữ liệu hợp lý.");
//        } catch (Exception e) {
//            return ResponseEntity.badRequest().body(" Lỗi: " + e.getMessage());
//        }
//    }
//
//    private String generateProductName(String category, Faker faker, Random rand) {
//        switch (category) {
//            case "điện thoại":
//                return faker.options().option("iPhone", "Samsung Galaxy", "Xiaomi", "OPPO", "Realme") + " " +
//                        faker.bothify("A##") + " " +
//                        faker.options().option("5G", "Plus", "Pro", "Ultra", "Lite");
//
//            case "máy tính":
//                return faker.options().option("MacBook Air", "Dell XPS", "Asus ROG", "HP Envy", "Lenovo Legion") + " " +
//                        faker.options().option("M3", "Ryzen 7", "i7 12th Gen", "i5 13th Gen");
//
//            case "màn hình":
//                return faker.options().option("Asus ProArt", "LG UltraGear", "Samsung Odyssey") + " " +
//                        faker.number().numberBetween(24, 34) + " inch " +
//                        faker.options().option("Full HD", "2K", "4K");
//
//            case "phụ kiện":
//                return faker.options().option("Cáp Type-C", "Sạc nhanh PD", "Hub USB-C", "Adapter 65W") + " " +
//                        faker.options().option("UGreen", "Anker", "Baseus");
//
//            case "smartwatch":
//                return faker.options().option("Apple Watch", "Galaxy Watch", "Amazfit", "Huawei Watch") + " " +
//                        faker.options().option("S10", "GT4", "Pro 2", "Active 3");
//
//            case "đồng hồ":
//                return faker.options().option("Casio", "Orient", "Citizen", "Seiko", "DW") + " " +
//                        faker.bothify("MTP-###L-7AVDF");
//
//            case "âm thanh":
//                return faker.options().option("Sony WH", "AirPods Pro", "JBL Flip", "Harman Kardon") + " " +
//                        faker.number().numberBetween(2, 5);
//
//            default:
//                return "Sản phẩm " + faker.number().digits(4);
//        }
//    }



}
