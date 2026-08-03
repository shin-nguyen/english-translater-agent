package com.example.translator.note;

import com.example.translator.note.NoteDtos.NoteCreateRequest;
import com.example.translator.note.NoteDtos.NoteResponse;
import com.example.translator.note.NoteDtos.NoteSummaryResponse;
import com.example.translator.note.NoteDtos.NoteUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notes")
public class NoteController {

    private final NoteService noteService;

    public NoteController(NoteService noteService) {
        this.noteService = noteService;
    }

    @GetMapping
    public Page<NoteSummaryResponse> search(
            @RequestParam(required = false) Long roleId,
            @RequestParam(required = false) Long contextId,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable
    ) {
        return noteService.search(roleId, contextId, keyword, pageable);
    }

    @GetMapping("/{id}")
    public NoteResponse getById(@PathVariable Long id) {
        return noteService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NoteResponse create(@Valid @RequestBody NoteCreateRequest request) {
        return noteService.create(request);
    }

    @PutMapping("/{id}")
    public NoteResponse update(@PathVariable Long id, @Valid @RequestBody NoteUpdateRequest request) {
        return noteService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        noteService.delete(id);
    }
}
