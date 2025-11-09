package io.hhplus.ecommerce.presentation.dto

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 상품 목록 응답 DTO
 */
@Schema(description = "상품 목록 응답")
data class ProductListResponse(
    @Schema(description = "상품 목록")
    val data: List<ProductDto>,

    @Schema(description = "페이지네이션 정보")
    val pagination: PaginationInfo
)

/**
 * 상품 상세 응답 DTO
 */
@Schema(description = "상품 상세 응답")
data class ProductDetailResponse(
    @Schema(description = "상품 ID")
    val id: String,

    @Schema(description = "상품명")
    val name: String,

    @Schema(description = "브랜드")
    val brand: String,

    @Schema(description = "카테고리")
    val category: String,

    @Schema(description = "상품 설명")
    val description: String?,

    @Schema(description = "기본가격 (원)")
    val basePrice: Long,

    @Schema(description = "판매가격 (원)")
    val salePrice: Long,

    @Schema(description = "할인율 (%)")
    val discountRate: Int,

    @Schema(description = "이미지 URL 목록")
    val images: List<String>,

    @Schema(description = "변량 목록")
    val variants: List<ProductVariantDto>,

    @Schema(description = "평점 (0-5)")
    val rating: Double,

    @Schema(description = "리뷰 개수")
    val reviewCount: Int
)

/**
 * 상품 기본 정보 DTO
 */
@Schema(description = "상품 정보")
data class ProductDto(
    @Schema(description = "상품 ID")
    val id: String,

    @Schema(description = "상품명")
    val name: String,

    @Schema(description = "브랜드")
    val brand: String,

    @Schema(description = "카테고리")
    val category: String,

    @Schema(description = "기본가격 (원)")
    val basePrice: Long,

    @Schema(description = "판매가격 (원)")
    val salePrice: Long,

    @Schema(description = "할인율 (%)")
    val discountRate: Int,

    @Schema(description = "이미지 URL 목록")
    val images: List<String>,

    @Schema(description = "변량 개수")
    val variantCount: Int,

    @Schema(description = "평점 (0-5)")
    val rating: Double,

    @Schema(description = "리뷰 개수")
    val reviewCount: Int,

    @Schema(description = "태그 목록")
    val tags: List<String> = emptyList()
)

/**
 * 상품 변량(SKU) DTO
 */
@Schema(description = "상품 변량 정보")
data class ProductVariantDto(
    @Schema(description = "변량 ID")
    val id: String,

    @Schema(description = "SKU 코드")
    val sku: String,

    @Schema(description = "색상")
    val color: String,

    @Schema(description = "색상 HEX 코드")
    val colorHex: String,

    @Schema(description = "사이즈")
    val size: String,

    @Schema(description = "길이")
    val length: String?,

    @Schema(description = "판매가격 (원)")
    val price: Long,

    @Schema(description = "원가 (원)")
    val originalPrice: Long,

    @Schema(description = "재고수량")
    val stock: Int,

    @Schema(description = "재고 상태: IN_STOCK, LOW_STOCK, OUT_OF_STOCK")
    val stockStatus: String
)

/**
 * 페이지네이션 정보
 */
@Schema(description = "페이지네이션 정보")
data class PaginationInfo(
    @Schema(description = "현재 페이지 (1부터 시작)")
    val page: Int,

    @Schema(description = "페이지당 항목 수")
    val limit: Int,

    @Schema(description = "전체 항목 수")
    val total: Int,

    @Schema(description = "전체 페이지 수")
    val totalPages: Int
)