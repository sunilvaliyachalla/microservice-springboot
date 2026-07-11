package com.ecommerce.product.service;

import com.ecommerce.product.entity.Product;
import com.ecommerce.product.exception.InsufficientStockException;
import com.ecommerce.product.exception.ResourceNotFoundException;
import com.ecommerce.product.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    private Product product(Long id, String name, String price, int stock) {
        return new Product(id, name, new BigDecimal(price), stock);
    }

    @Test
    void getAllProductsReturnsAll() {
        when(productRepository.findAll()).thenReturn(List.of(
                product(1L, "Keyboard", "99.99", 10),
                product(2L, "Mouse", "49.50", 5)));

        List<Product> result = productService.getAllProducts();

        assertEquals(2, result.size());
        verify(productRepository).findAll();
    }

    @Test
    void getProductByIdReturnsProductWhenPresent() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product(1L, "Keyboard", "99.99", 10)));

        Optional<Product> result = productService.getProductById(1L);

        assertTrue(result.isPresent());
        assertEquals("Keyboard", result.get().getName());
    }

    @Test
    void getProductByIdReturnsEmptyWhenMissing() {
        when(productRepository.findById(42L)).thenReturn(Optional.empty());

        assertTrue(productService.getProductById(42L).isEmpty());
    }

    @Test
    void createProductForcesServerAssignedId() {
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        Product incoming = product(999L, "Keyboard", "99.99", 10);

        Product created = productService.createProduct(incoming);

        assertNull(created.getId(), "client-supplied id must be discarded (mass-assignment protection)");
        verify(productRepository).save(incoming);
    }

    @Test
    void createProductDefaultsNullStockToZero() {
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        Product incoming = new Product(null, "Keyboard", new BigDecimal("99.99"), null);

        Product created = productService.createProduct(incoming);

        assertEquals(0, created.getStock());
    }

    @Test
    void updateProductUpdatesFields() {
        Product existing = product(1L, "Old", "10.00", 3);
        when(productRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product updated = productService.updateProduct(1L, product(null, "New", "20.00", 7));

        assertEquals("New", updated.getName());
        assertEquals(new BigDecimal("20.00"), updated.getPrice());
        assertEquals(7, updated.getStock());
    }

    @Test
    void updateProductKeepsStockWhenDetailsStockIsNull() {
        Product existing = product(1L, "Old", "10.00", 3);
        when(productRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product updated = productService.updateProduct(1L,
                new Product(null, "New", new BigDecimal("20.00"), null));

        assertEquals(3, updated.getStock());
    }

    @Test
    void updateProductThrowsWhenMissing() {
        when(productRepository.findById(42L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> productService.updateProduct(42L, product(null, "X", "1.00", 1)));
        verify(productRepository, never()).save(any());
    }

    @Test
    void deleteProductDeletesExisting() {
        Product existing = product(1L, "Keyboard", "99.99", 10);
        when(productRepository.findById(1L)).thenReturn(Optional.of(existing));

        productService.deleteProduct(1L);

        verify(productRepository).delete(existing);
    }

    @Test
    void deleteProductThrowsWhenMissing() {
        when(productRepository.findById(42L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> productService.deleteProduct(42L));
        verify(productRepository, never()).delete(any());
    }

    @Test
    void decreaseStockReducesStock() {
        Product existing = product(1L, "Keyboard", "99.99", 10);
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(existing));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product result = productService.decreaseStock(1L, 4);

        assertEquals(6, result.getStock());
    }

    @Test
    void decreaseStockAllowsDrainingToZero() {
        Product existing = product(1L, "Keyboard", "99.99", 5);
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(existing));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        assertEquals(0, productService.decreaseStock(1L, 5).getStock());
    }

    @Test
    void decreaseStockThrowsWhenInsufficient() {
        Product existing = product(1L, "Keyboard", "99.99", 2);
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(existing));

        assertThrows(InsufficientStockException.class, () -> productService.decreaseStock(1L, 3));
        assertEquals(2, existing.getStock(), "stock must be unchanged after a rejected decrement");
        verify(productRepository, never()).save(any());
    }

    @Test
    void decreaseStockRejectsNonPositiveQuantity() {
        assertThrows(IllegalArgumentException.class, () -> productService.decreaseStock(1L, 0));
        assertThrows(IllegalArgumentException.class, () -> productService.decreaseStock(1L, -5));
        verify(productRepository, never()).findByIdForUpdate(anyLong());
    }

    @Test
    void decreaseStockThrowsWhenProductMissing() {
        when(productRepository.findByIdForUpdate(42L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> productService.decreaseStock(42L, 1));
    }
}
