package lt.sharenote.pagination;

import lt.sharenote.exception.ApiException;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PaginationTest {
    @Test void supportsOptionalParametersAndSafeOffsets() {
        assertThat(Pagination.optional(null, null)).isNull();
        assertThat(Pagination.optional(null, 25)).isEqualTo(new Pagination(0, 25));
        assertThat(Pagination.optional(2, null)).isEqualTo(new Pagination(2, 10));
        assertThat(Pagination.optional(Integer.MAX_VALUE, 100).offset()).isEqualTo(214748364700L);
    }

    @Test void rejectsInvalidBounds() {
        assertThatThrownBy(() -> Pagination.optional(-1, 10)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> Pagination.optional(0, 0)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> Pagination.optional(0, 101)).isInstanceOf(ApiException.class);
    }
}
