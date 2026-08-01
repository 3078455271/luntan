package com.luntan.forum.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "post_likes")
public class PostLikeEntity {

    @EmbeddedId
    private PostLikeId id;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PostLikeEntity() {
    }

    public PostLikeEntity(PostLikeId id) {
        this.id = id;
    }

    public PostLikeId getId() { return id; }
}