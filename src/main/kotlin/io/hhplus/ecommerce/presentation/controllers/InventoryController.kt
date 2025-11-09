package io.hhplus.ecommerce.presentation.controllers

import io.hhplus.ecommerce.dto.DeductInventoryRequest
import io.hhplus.ecommerce.dto.DeductInventoryResponse
import io.hhplus.ecommerce.dto.InventoryResponse
import io.hhplus.ecommerce.dto.ReserveInventoryRequest
import io.hhplus.ecommerce.dto.ReserveInventoryResponse
import io.hhplus.ecommerce.application.usecases.InventoryUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/inventory")
@Tag(name = "Inventory", description = "재고 관리 API")
class InventoryController(
    private val inventoryUseCase: InventoryUseCase
) {

    @GetMapping("/skus/{sku}")
    @Operation(
        summary = "재고 조회",
        description = "SKU 코드로 실시간 재고 정보를 조회합니다."
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "조회 성공",
                content = [Content(mediaType = "application/json")]
            ),
            ApiResponse(
                responseCode = "404",
                description = "SKU를 찾을 수 없음"
            )
        ]
    )
    fun getInventory(
        @Parameter(
            name = "sku",
            description = "SKU 코드",
            example = "LEVI-501-BLK-32-REG"
        )
        @PathVariable sku: String
    ): ResponseEntity<InventoryResponse> {
        val inventory = inventoryUseCase.getInventoryBySku(sku)
            ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(
            InventoryResponse(
                sku = inventory.sku,
                available = inventory.getAvailableStock(),
                reserved = inventory.reservedStock,
                physical = inventory.physicalStock,
                safetyStock = inventory.safetyStock,
                status = inventory.getStatus().name,
                lastUpdated = inventory.lastUpdated.toString()
            )
        )
    }

    @PostMapping("/reserve")
    @Operation(
        summary = "재고 예약",
        description = "주문 생성 시 재고를 예약합니다. 15분 TTL(Time To Live) 이후 자동 해제됩니다."
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "예약 성공"
            ),
            ApiResponse(
                responseCode = "409",
                description = "재고 부족 (예약 실패)"
            ),
            ApiResponse(
                responseCode = "404",
                description = "SKU를 찾을 수 없음"
            )
        ]
    )
    fun reserveInventory(
        @RequestBody request: ReserveInventoryRequest
    ): ResponseEntity<Any> {
        val reservation = inventoryUseCase.reserveInventory(request.sku, request.quantity, 15)

        return if (reservation != null) {
            ResponseEntity.ok(
                ReserveInventoryResponse(
                    reservationId = reservation.id,
                    sku = reservation.sku,
                    quantity = reservation.quantity,
                    expiresAt = reservation.expiresAt,
                    success = true
                )
            )
        } else {
            ResponseEntity.status(HttpStatus.CONFLICT)
                .body(
                    mapOf(
                        "code" to "INSUFFICIENT_STOCK",
                        "message" to "재고가 부족합니다.",
                        "details" to mapOf(
                            "sku" to request.sku,
                            "requestedQuantity" to request.quantity
                        )
                    )
                )
        }
    }

    @PostMapping("/deduct")
    @Operation(
        summary = "재고 차감",
        description = "결제 승인 후 실제 재고를 차감합니다."
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "차감 성공"
            ),
            ApiResponse(
                responseCode = "409",
                description = "재고 부족 (차감 실패)"
            ),
            ApiResponse(
                responseCode = "404",
                description = "SKU를 찾을 수 없음"
            )
        ]
    )
    fun deductInventory(
        @RequestBody request: DeductInventoryRequest
    ): ResponseEntity<Any> {
        val success = inventoryUseCase.deductInventory(request.sku, request.quantity)

        return if (success) {
            val inventory = inventoryUseCase.getInventoryBySku(request.sku)!!
            ResponseEntity.ok(
                DeductInventoryResponse(
                    sku = request.sku,
                    remainingStock = inventory.getAvailableStock(),
                    success = true,
                    message = "재고 차감 완료"
                )
            )
        } else {
            ResponseEntity.status(HttpStatus.CONFLICT)
                .body(
                    mapOf(
                        "code" to "INSUFFICIENT_STOCK",
                        "message" to "차감할 재고가 부족합니다.",
                        "details" to mapOf(
                            "sku" to request.sku,
                            "requestedQuantity" to request.quantity
                        )
                    )
                )
        }
    }

    @PostMapping("/cancel-reservation")
    @Operation(
        summary = "예약 취소",
        description = "결제 실패 또는 주문 취소 시 예약된 재고를 해제합니다."
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "취소 성공"
            ),
            ApiResponse(
                responseCode = "404",
                description = "SKU를 찾을 수 없음"
            )
        ]
    )
    fun cancelReservation(
        @RequestBody request: ReserveInventoryRequest
    ): ResponseEntity<Any> {
        val success = inventoryUseCase.cancelReservation(request.sku, request.quantity)

        return if (success) {
            val inventory = inventoryUseCase.getInventoryBySku(request.sku)!!
            ResponseEntity.ok(
                mapOf(
                    "message" to "예약 취소 완료",
                    "sku" to request.sku,
                    "availableStock" to inventory.getAvailableStock()
                )
            )
        } else {
            ResponseEntity.notFound().build()
        }
    }
}
