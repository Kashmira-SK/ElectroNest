package lk.sliit.electronest.search.controller;

import lk.sliit.electronest.search.service.ProductSearchService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ProductSearchControllerTest {
    @Test
    void paginationUsesStableProductOrdering() {
        var service = mock(ProductSearchService.class);
        var controller = new ProductSearchController(service);
        controller.searchProducts("phone", null, null, null, null, true, null, null, 1, 12);
        var page = ArgumentCaptor.forClass(Pageable.class);
        verify(service).search(eq("phone"), isNull(), isNull(), isNull(), isNull(), eq(true), isNull(), isNull(), page.capture());
        assertEquals(1, page.getValue().getPageNumber());
        assertEquals(Sort.Direction.DESC, page.getValue().getSort().getOrderFor("id").getDirection());
    }
}
