package com.example.translator.context;

import com.example.translator.common.ResourceNotFoundException;
import com.example.translator.context.ContextDtos.ContextRequest;
import com.example.translator.context.ContextDtos.ContextResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContextServiceTest {

    @Mock
    private ContextRepository contextRepository;

    @InjectMocks
    private ContextService contextService;

    @Test
    void findAll_returnsContextsOrderedByName() {
        Context meeting = new Context("Meeting online", "spoken");
        when(contextRepository.findAllByOrderByNameAsc()).thenReturn(List.of(meeting));

        List<ContextResponse> result = contextService.findAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Meeting online");
    }

    @Test
    void create_trimsNameAndSaves() {
        when(contextRepository.save(any(Context.class))).thenAnswer(inv -> inv.getArgument(0));

        ContextResponse response = contextService.create(new ContextRequest("  Team chat  ", "casual"));

        assertThat(response.name()).isEqualTo("Team chat");
        verify(contextRepository).save(any(Context.class));
    }

    @Test
    void getEntity_throwsWhenNotFound() {
        when(contextRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> contextService.getEntity(42L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_removesExistingContext() {
        Context context = new Context("Email/Report", "formal");
        when(contextRepository.findById(1L)).thenReturn(Optional.of(context));

        contextService.delete(1L);

        verify(contextRepository).delete(context);
    }
}
