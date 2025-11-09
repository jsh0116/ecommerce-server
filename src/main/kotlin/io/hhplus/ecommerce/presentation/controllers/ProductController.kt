package io.hhplus.ecommerce.presentation.controllers

import io.hhplus.ecommerce.dto.PaginationInfo
import io.hhplus.ecommerce.dto.ProductDetailResponse
import io.hhplus.ecommerce.dto.ProductDto
import io.hhplus.ecommerce.dto.ProductListResponse
import io.hhplus.ecommerce.dto.ProductVariantDto
import io.hhplus.ecommerce.application.usecases.ProductUseCase
import io.hhplus.ecommerce.application.usecases.InventoryUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Products", description = "상품 API")
class ProductController(
    private val productUseCase: ProductUseCase,
    private val inventoryUseCase: InventoryUseCase
) {

    @GetMapping
    @Operation(
        summary = "상품 목록 조회",
        description = "카테고리, 브랜드, 가격 범위로 필터링하여 상품 목록을 조회합니다."
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "조회 성공",
                content = [Content(mediaType = "application/json")]
            ),
            ApiResponse(
                responseCode = "400",
                description = "잘못된 요청 파라미터"
            )
        ]
    )
    fun getProducts(
        @Parameter(
            name = "page",
            description = "페이지 번호 (기본값: 1)",
            example = "1"
        )
        @RequestParam(defaultValue = "1") page: Int,

        @Parameter(
            name = "limit",
            description = "페이지당 항목 수 (기본값: 20, 최대: 100)",
            example = "20"
        )
        @RequestParam(defaultValue = "20") limit: Int,

        @Parameter(
            name = "category",
            description = "카테고리 (예: pants, shoes)",
            example = "pants"
        )
        @RequestParam(required = false) category: String?,

        @Parameter(
            name = "brand",
            description = "브랜드명 (예: LEVI'S, NIKE)",
            example = "LEVI'S"
        )
        @RequestParam(required = false) brand: String?,

        @Parameter(
            name = "minPrice",
            description = "최소 가격 (원)",
            example = "50000"
        )
        @RequestParam(required = false) minPrice: Long?,

        @Parameter(
            name = "maxPrice",
            description = "최대 가격 (원)",
            example = "100000"
        )
        @RequestParam(required = false) maxPrice: Long?
    ): ResponseEntity<ProductListResponse> {
        // TODO: Implement proper pagination, filtering, and product variant support
        val products = productUseCase.getProducts(category, "name")

        val filteredProducts = products
            .filter { p ->
                (category == null || p.category.equals(category, ignoreCase = true)) &&
                (minPrice == null || p.price >= minPrice) &&
                (maxPrice == null || p.price <= maxPrice)
            }

        val productDtos = filteredProducts.map { p ->
            ProductDto(
                id = p.id,
                name = p.name,
                brand = "Brand",
                category = p.category,
                basePrice = p.price,
                salePrice = p.price,
                discountRate = 0,
                images = listOf("https://example.com/image.jpg"),
                variantCount = 1,
                rating = 4.5,
                reviewCount = 100,
                tags = emptyList()
            )
        }

        val totalPages = (filteredProducts.size + limit - 1) / limit

        return ResponseEntity.ok(
            ProductListResponse(
                data = productDtos,
                pagination = PaginationInfo(
                    page = page,
                    limit = limit,
                    total = filteredProducts.size,
                    totalPages = totalPages
                )
            )
        )
    }

    @GetMapping("/{productId}")
    @Operation(
        summary = "상품 상세 조회",
        description = "상품 ID로 상품의 상세 정보 및 모든 변량을 조회합니다."
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "조회 성공"
            ),
            ApiResponse(
                responseCode = "404",
                description = "상품을 찾을 수 없음"
            )
        ]
    )
    fun getProductDetail(
        @Parameter(
            name = "productId",
            description = "상품 ID",
            example = "prod_001"
        )
        @PathVariable productId: String
    ): ResponseEntity<ProductDetailResponse> {
        val product = productUseCase.getProductById(productId)
            ?: return ResponseEntity.notFound().build()

        // 재고 조회
        val inventory = inventoryUseCase.getInventoryBySku(product.id)
        val availableStock = inventory?.getAvailableStock() ?: 0
        val stockStatus = if (availableStock > 0) "IN_STOCK" else "OUT_OF_STOCK"

        // TODO: Implement product variant support when ProductService.getProductVariants is implemented
        val variantDtos = listOf(
            ProductVariantDto(
                id = "variant_001",
                sku = "SKU-${product.id}-001",
                color = "Black",
                colorHex = "#000000",
                size = "M",
                length = "Regular",
                price = product.price,
                originalPrice = product.price,
                stock = availableStock,
                stockStatus = stockStatus
            )
        )

        return ResponseEntity.ok(
            ProductDetailResponse(
                id = product.id,
                name = product.name,
                brand = "Brand",
                category = product.category,
                description = product.description,
                basePrice = product.price,
                salePrice = product.price,
                discountRate = 0,
                images = listOf("https://example.com/image.jpg"),
                variants = variantDtos,
                rating = 4.5,
                reviewCount = 100
            )
        )
    }

    @GetMapping("/{productId}/variants")
    @Operation(
        summary = "상품 변량 목록 조회",
        description = "상품의 모든 변량(SKU)을 색상, 사이즈로 필터링하여 조회합니다."
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "조회 성공"
            ),
            ApiResponse(
                responseCode = "404",
                description = "상품을 찾을 수 없음"
            )
        ]
    )
    fun getVariants(
        @Parameter(
            name = "productId",
            description = "상품 ID",
            example = "prod_001"
        )
        @PathVariable productId: String,

        @Parameter(
            name = "color",
            description = "색상 필터",
            example = "black"
        )
        @RequestParam(required = false) color: String?,

        @Parameter(
            name = "size",
            description = "사이즈 필터",
            example = "32"
        )
        @RequestParam(required = false) size: String?,

        @Parameter(
            name = "inStock",
            description = "재고 있는 변량만",
            example = "true"
        )
        @RequestParam(required = false) inStock: Boolean?
    ): ResponseEntity<List<ProductVariantDto>> {
        val product = productUseCase.getProductById(productId)
            ?: return ResponseEntity.notFound().build()

        // 재고 조회
        val inventory = inventoryUseCase.getInventoryBySku(product.id)
        val availableStock = inventory?.getAvailableStock() ?: 0
        val stockStatus = if (availableStock > 0) "IN_STOCK" else "OUT_OF_STOCK"

        // TODO: Implement product variant support when ProductService.getProductVariants is implemented
        val variants = listOf(
            ProductVariantDto(
                id = "variant_001",
                sku = "SKU-${product.id}-001",
                color = "Black",
                colorHex = "#000000",
                size = "M",
                length = "Regular",
                price = product.price,
                originalPrice = product.price,
                stock = availableStock,
                stockStatus = stockStatus
            )
        ).filter { v ->
            (color == null || v.color.equals(color, ignoreCase = true)) &&
            (size == null || v.size.equals(size, ignoreCase = true)) &&
            (inStock == null || (inStock == false || v.stock > 0))
        }

        return ResponseEntity.ok(variants)
    }

    @GetMapping("/search")
    @Operation(
        summary = "상품 검색",
        description = "키워드로 상품을 검색합니다."
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "검색 성공"
            )
        ]
    )
    fun searchProducts(
        @Parameter(
            name = "q",
            description = "검색 키워드",
            example = "청바지",
            required = true
        )
        @RequestParam q: String,

        @Parameter(
            name = "page",
            description = "페이지 번호",
            example = "1"
        )
        @RequestParam(defaultValue = "1") page: Int,

        @Parameter(
            name = "limit",
            description = "페이지당 항목 수",
            example = "20"
        )
        @RequestParam(defaultValue = "20") limit: Int
    ): ResponseEntity<ProductListResponse> {
        // TODO: Implement proper search functionality with pagination and filtering
        val products = productUseCase.getProducts(null, "name")

        val searchResults = products.filter { p ->
            p.name.contains(q, ignoreCase = true) ||
            p.description?.contains(q, ignoreCase = true) == true
        }

        val productDtos = searchResults.map { p ->
            ProductDto(
                id = p.id,
                name = p.name,
                brand = "Brand",
                category = p.category,
                basePrice = p.price,
                salePrice = p.price,
                discountRate = 0,
                images = listOf("https://example.com/image.jpg"),
                variantCount = 1,
                rating = 4.5,
                reviewCount = 100,
                tags = emptyList()
            )
        }

        val totalPages = (searchResults.size + limit - 1) / limit

        return ResponseEntity.ok(
            ProductListResponse(
                data = productDtos,
                pagination = PaginationInfo(
                    page = page,
                    limit = limit,
                    total = searchResults.size,
                    totalPages = totalPages
                )
            )
        )
    }
}
