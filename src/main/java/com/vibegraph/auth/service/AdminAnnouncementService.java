package com.vibegraph.auth.service;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vibegraph.auth.domain.Announcement;
import com.vibegraph.auth.dto.AnnouncementRequest;
import com.vibegraph.auth.dto.AnnouncementResponse;
import com.vibegraph.auth.repository.AnnouncementRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminAnnouncementService {

    private final AnnouncementRepository announcementRepository;

    @Transactional(readOnly = true)
    public List<AnnouncementResponse> list() {
        return announcementRepository.findAll().stream()
                .sorted(Comparator.comparing(Announcement::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(AnnouncementResponse::from)
                .toList();
    }

    @Transactional
    public AnnouncementResponse create(AnnouncementRequest request) {
        return AnnouncementResponse.from(announcementRepository.save(toAnnouncement(
                Announcement.builder().build(), request)));
    }

    @Transactional
    public AnnouncementResponse update(UUID id, AnnouncementRequest request) {
        Announcement announcement = announcementRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Announcement not found: " + id));
        return AnnouncementResponse.from(announcementRepository.save(toAnnouncement(announcement, request)));
    }

    @Transactional
    public void delete(UUID id) {
        announcementRepository.deleteById(id);
    }

    @Transactional
    public AnnouncementResponse disable(UUID id) {
        Announcement announcement = announcementRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Announcement not found: " + id));
        announcement.setActive(false);
        return AnnouncementResponse.from(announcementRepository.save(announcement));
    }

    private Announcement toAnnouncement(Announcement announcement, AnnouncementRequest request) {
        if (request.startsAt() != null && request.endsAt() != null
                && !request.endsAt().isAfter(request.startsAt())) {
            throw new IllegalArgumentException("Announcement end time must be after start time");
        }
        announcement.setType(request.type());
        announcement.setSeverity(request.severity());
        announcement.setTarget(request.target());
        announcement.setTitle(toPlainText(request.title()));
        announcement.setBody(toPlainText(request.body()));
        announcement.setStartsAt(request.startsAt());
        announcement.setEndsAt(request.endsAt());
        announcement.setDismissible(request.dismissible());
        announcement.setActive(request.active());
        return announcement;
    }

    private String toPlainText(String value) {
        return value.replaceAll("<[^>]*>", "").trim();
    }
}
