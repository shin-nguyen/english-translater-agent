package com.example.translator.note;

import com.example.translator.common.ResourceNotFoundException;
import com.example.translator.context.Context;
import com.example.translator.context.ContextRepository;
import com.example.translator.note.NoteDtos.NoteCreateRequest;
import com.example.translator.note.NoteDtos.NoteResponse;
import com.example.translator.note.NoteDtos.NoteSummaryResponse;
import com.example.translator.note.NoteDtos.NoteUpdateRequest;
import com.example.translator.role.Role;
import com.example.translator.role.RoleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class NoteService {

    private final NoteRepository noteRepository;
    private final RoleRepository roleRepository;
    private final ContextRepository contextRepository;

    public NoteService(NoteRepository noteRepository, RoleRepository roleRepository, ContextRepository contextRepository) {
        this.noteRepository = noteRepository;
        this.roleRepository = roleRepository;
        this.contextRepository = contextRepository;
    }

    @Transactional(readOnly = true)
    public Page<NoteSummaryResponse> search(Long roleId, Long contextId, String keyword, Pageable pageable) {
        Specification<Note> spec = Specification.where(NoteSpecifications.hasRole(roleId))
                .and(NoteSpecifications.hasContext(contextId))
                .and(NoteSpecifications.keywordMatches(keyword));
        return noteRepository.findAll(spec, pageable).map(NoteSummaryResponse::from);
    }

    @Transactional(readOnly = true)
    public NoteResponse getById(Long id) {
        return NoteResponse.from(getEntity(id));
    }

    private Note getEntity(Long id) {
        return noteRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Note", id));
    }

    public NoteResponse create(NoteCreateRequest request) {
        Note note = new Note();
        applyCommonFields(note, request.title(), request.originalText(), request.detectedLanguage(),
                request.englishResult(), request.alternatives(), request.analysis(), request.roleId(), request.contextId());
        return NoteResponse.from(noteRepository.save(note));
    }

    public NoteResponse update(Long id, NoteUpdateRequest request) {
        Note note = getEntity(id);
        applyCommonFields(note, request.title(), request.originalText(), request.detectedLanguage(),
                request.englishResult(), request.alternatives(), request.analysis(), request.roleId(), request.contextId());
        return NoteResponse.from(noteRepository.save(note));
    }

    public void delete(Long id) {
        Note note = getEntity(id);
        noteRepository.delete(note);
    }

    private void applyCommonFields(Note note, String title, String originalText, String detectedLanguage,
                                    String englishResult, java.util.List<NoteAlternativeItem> alternatives,
                                    java.util.List<NoteAnalysisItem> analysis, Long roleId, Long contextId) {
        note.setTitle(title);
        note.setOriginalText(originalText);
        note.setDetectedLanguage(detectedLanguage);
        note.setEnglishResult(englishResult);
        note.setAlternatives(alternatives);
        note.setAnalysis(analysis);

        if (roleId != null) {
            Role role = roleRepository.findById(roleId)
                    .orElseThrow(() -> ResourceNotFoundException.of("Role", roleId));
            note.setRole(role);
        } else {
            note.setRole(null);
        }

        if (contextId != null) {
            Context context = contextRepository.findById(contextId)
                    .orElseThrow(() -> ResourceNotFoundException.of("Context", contextId));
            note.setContext(context);
        } else {
            note.setContext(null);
        }
    }
}
