package io.hhplus.week2.controller

import io.hhplus.week2.dto.PaginationInfo
import io.hhplus.week2.dto.ProductDetailResponse
import io.hhplus.week2.dto.ProductDto
import io.hhplus.week2.dto.ProductListResponse
import io.hhplus.week2.dto.ProductVariantDto
import io.hhplus.week2.service.ProductService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
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
    private val productService: ProductService
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
        val (products, totalCount) = productService.getAllProducts(page, limit)

        val filteredProducts = products
            .filter { p ->
                (category == null || p.category.equals(category, ignoreCase = true)) &&
                (brand == null || p.brand.equals(brand, ignoreCase = true)) &&
                (minPrice == null || p.salePrice >= minPrice) &&
                (maxPrice == null || p.salePrice <= maxPrice)
            }

        val productDtos = filteredProducts.map { p ->
            val variantCount = productService.getProductVariants(p.id).size
            ProductDto(
                id = p.id,
                name = p.name,
                brand = p.brand,
                category = p.category,
                basePrice = p.basePrice,
                salePrice = p.salePrice,
                discountRate = p.discountRate,
                images = p.images,
                variantCount = variantCount,
                rating = p.rating,
                reviewCount = p.reviewCount,
                tags = p.tags
            )
        }

        val totalPages = (totalCount + limit - 1) / limit

        return ResponseEntity.ok(
            ProductListResponse(
                data = productDtos,
                pagination = PaginationInfo(
                    page = page,
                    limit = limit,
                    total = totalCount,
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
        val product = productService.getProductById(productId)
            ?: return ResponseEntity.notFound().build()

        val variants = productService.getProductVariants(productId)
        val variantDtos = variants.map { v ->
            ProductVariantDto(
                id = v.id,
                sku = v.sku,
                color = v.color,
                colorHex = v.colorHex,
                size = v.size,
                length = v.length,
                price = v.price,
                originalPrice = v.originalPrice,
                stock = v.stock,
                stockStatus = v.stockStatus.name
            )
        }

        return ResponseEntity.ok(
            ProductDetailResponse(
                id = product.id,
                name = product.name,
                brand = product.brand,
                category = product.category,
                description = product.description,
                basePrice = product.basePrice,
                salePrice = product.salePrice,
                discountRate = product.discountRate,
                images = product.images,
                variants = variantDtos,
                rating = product.rating,
                reviewCount = product.reviewCount
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
        val product = productService.getProductById(productId)
            ?: return ResponseEntity.notFound().build()

        val variants = productService.getProductVariants(productId)
            .filter { v ->
                (color == null || v.color.equals(color, ignoreCase = true)) &&
                (size == null || v.size.equals(size, ignoreCase = true)) &&
                (inStock == null || (inStock == false || v.stock > 0))
            }
            .map { v ->
                ProductVariantDto(
                    id = v.id,
                    sku = v.sku,
                    color = v.color,
                    colorHex = v.colorHex,
                    size = v.size,
                    length = v.length,
                    price = v.price,
                    originalPrice = v.originalPrice,
                    stock = v.stock,
                    stockStatus = v.stockStatus.name
                )
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
        val (products, totalCount) = productService.getAllProducts(page, limit)

        val searchResults = products.filter { p ->
            p.name.contains(q, ignoreCase = true) ||
            p.brand.contains(q, ignoreCase = true) ||
            p.description?.contains(q, ignoreCase = true) == true ||
            p.tags.any { it.contains(q, ignoreCase = true) }
        }

        val productDtos = searchResults.map { p ->
            val variantCount = productService.getProductVariants(p.id).size
            ProductDto(
                id = p.id,
                name = p.name,
                brand = p.brand,
                category = p.category,
                basePrice = p.basePrice,
                salePrice = p.salePrice,
                discountRate = p.discountRate,
                images = p.images,
                variantCount = variantCount,
                rating = p.rating,
                reviewCount = p.reviewCount,
                tags = p.tags
            )
        }

        val totalPages = (totalCount + limit - 1) / limit

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
