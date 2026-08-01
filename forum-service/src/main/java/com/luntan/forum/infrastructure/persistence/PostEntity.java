package com.luntan.forum.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "posts")
public class PostEntity {

    public enum Status { PUBLISHED, DELETED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "section_id", nullable = false)
    private SectionEntity section;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PUBLISHED;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "comment_count", nullable = false)
    private long commentCount;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    @Version
    @Column(nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected PostEntity() {
    }

    public PostEntity(SectionEntity section, Long authorId, String title, String content) {
        this.section = section;
        this.authorId = authorId;
        this.title = title;
        this.content = content;
    }

    public void update(SectionEntity section, String title, String content) {
        this.section = section;
        this.title = title;
        this.content = content;
    }

    public void delete(Instant now) {
        this.status = Status.DELETED;
        this.deletedAt = now;
    }

    public Long getId() { return id; }
    public SectionEntity getSection() { return section; }
    public Long getAuthorId() { return authorId; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public Status getStatus() { return status; }
    public long getViewCount() { return viewCount; }
    public long getCommentCount() { return commentCount; }
    public long getLikeCount() { return likeCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}