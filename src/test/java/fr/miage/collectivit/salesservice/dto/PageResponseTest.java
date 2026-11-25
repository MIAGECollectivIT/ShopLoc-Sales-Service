package fr.miage.collectivit.salesservice.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@DisplayName("PageResponse Unit Tests")
class PageResponseTest {

  @Test
  @DisplayName("Should correctly map Spring Data Page to PageResponse")
  void shouldMapPageToPageResponse() {
    Page<String> page = new PageImpl<>(List.of("item1", "item2"), PageRequest.of(0, 10), 2);

    PageResponse<String> response = PageResponse.from(page);

    assertThat(response.content()).containsExactly("item1", "item2");
    assertThat(response.page()).isZero();
    assertThat(response.size()).isEqualTo(10);
    assertThat(response.totalElements()).isEqualTo(2);
    assertThat(response.totalPages()).isEqualTo(1);
    assertThat(response.first()).isTrue();
    assertThat(response.last()).isTrue();
  }
}
