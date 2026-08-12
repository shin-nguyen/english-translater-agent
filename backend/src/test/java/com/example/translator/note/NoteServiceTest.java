package com.example.translator.note;

import com.example.translator.common.ResourceNotFoundException;
import com.example.translator.context.Context;
import com.example.translator.context.ContextRepository;
import com.example.translator.note.NoteDtos.NoteCreateRequest;
import com.example.translator.note.NoteDtos.NoteResponse;
import com.example.translator.note.NoteDtos.NoteSummaryResponse;
import com.example.translator.role.Role;
import com.example.translator.role.RoleRepository;
import com.example.translator.user.AppRole;
import com.example.translator.user.AppUser;
import com.example.translator.user.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NoteServiceTest {

    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;

    @Mock
    private NoteRepository noteRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private ContextRepository contextRepository;
    @Mock
    private AppUserRepository appUserRepository;

    @InjectMocks
    private NoteService noteService;

    private static AppUser userWithId(Long id) {
        AppUser user = new AppUser("user@example.com", "hash", "User", AppRole.USER);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @Test
    void create_resolvesRoleAndContextWhenIdsProvided() {
        Role role = new Role("Developer", "tech");
        Context context = new Context("Team chat", "casual");
        when(appUserRepository.getReferenceById(OWNER_ID)).thenReturn(userWithId(OWNER_ID));
        when(roleRepository.findById(1L)).thenReturn(Optional.of(role));
        when(contextRepository.findById(2L)).thenReturn(Optional.of(context));
        when(noteRepository.save(any(Note.class))).thenAnswer(inv -> inv.getArgument(0));

        NoteCreateRequest request = new NoteCreateRequest(
                "Xin gia han deadline", "xin gia han deadline task", "vi",
                "Requesting a deadline extension for the task",
                List.of(new NoteAlternativeItem("Could we extend the deadline?", "Casual", "For a quick team chat")),
                List.of(new NoteAnalysisItem("xin gia han", "extend the deadline", "ok")), 1L, 2L);

        NoteResponse response = noteService.create(OWNER_ID, request);

        assertThat(response.role().name()).isEqualTo("Developer");
        assertThat(response.context().name()).isEqualTo("Team chat");
        assertThat(response.englishResult()).isEqualTo("Requesting a deadline extension for the task");
    }

    @Test
    void create_allowsNullRoleAndContext() {
        when(appUserRepository.getReferenceById(OWNER_ID)).thenReturn(userWithId(OWNER_ID));
        when(noteRepository.save(any(Note.class))).thenAnswer(inv -> inv.getArgument(0));

        NoteCreateRequest request = new NoteCreateRequest(
                "Title", "original", "en", "result", List.of(), List.of(), null, null);

        NoteResponse response = noteService.create(OWNER_ID, request);

        assertThat(response.role()).isNull();
        assertThat(response.context()).isNull();
        verifyNoInteractions(roleRepository, contextRepository);
    }

    @Test
    void create_throwsWhenRoleIdNotFound() {
        when(appUserRepository.getReferenceById(OWNER_ID)).thenReturn(userWithId(OWNER_ID));
        when(roleRepository.findById(99L)).thenReturn(Optional.empty());

        NoteCreateRequest request = new NoteCreateRequest(
                "Title", "original", "en", "result", List.of(), List.of(), 99L, null);

        assertThatThrownBy(() -> noteService.create(OWNER_ID, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getById_throwsWhenNoteNotFound() {
        when(noteRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> noteService.getById(OWNER_ID, 5L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getById_throwsNotFoundWhenNoteBelongsToAnotherUser() {
        Note note = new Note();
        note.setUser(userWithId(OTHER_USER_ID));
        when(noteRepository.findById(5L)).thenReturn(Optional.of(note));

        assertThatThrownBy(() -> noteService.getById(OWNER_ID, 5L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_removesExistingNote() {
        Note note = new Note();
        note.setUser(userWithId(OWNER_ID));
        when(noteRepository.findById(3L)).thenReturn(Optional.of(note));

        noteService.delete(OWNER_ID, 3L);

        verify(noteRepository).delete(note);
    }

    @Test
    void delete_throwsNotFoundWhenNoteBelongsToAnotherUser() {
        Note note = new Note();
        note.setUser(userWithId(OTHER_USER_ID));
        when(noteRepository.findById(3L)).thenReturn(Optional.of(note));

        assertThatThrownBy(() -> noteService.delete(OWNER_ID, 3L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(noteRepository, never()).delete(any(Note.class));
    }

    @Test
    void search_delegatesToRepositoryWithSpecificationAndPageable() {
        Note note = new Note();
        note.setTitle("Test note");
        note.setOriginalText("orig");
        note.setEnglishResult("result");
        Pageable pageable = PageRequest.of(0, 20);
        Page<Note> page = new PageImpl<>(List.of(note), pageable, 1);
        when(noteRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

        Page<NoteSummaryResponse> result = noteService.search(OWNER_ID, 2L, 3L, "test", pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).title()).isEqualTo("Test note");
    }
}
