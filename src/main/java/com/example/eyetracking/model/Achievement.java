package com.example.eyetracking.model;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "achievements",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "badge_code"})
)
public class Achievement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "badge_code", nullable = false)
    private String badgeCode;

    @Column(nullable = false)
    private String badgeName;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private LocalDateTime unlockedAt;

    @PrePersist
    public void onCreate() {
        if (unlockedAt == null) {
            unlockedAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getBadgeCode() {
        return badgeCode;
    }

    public void setBadgeCode(String badgeCode) {
        this.badgeCode = badgeCode;
    }

    public String getBadgeName() {
        return badgeName;
    }

    public void setBadgeName(String badgeName) {
        this.badgeName = badgeName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getUnlockedAt() {
        return unlockedAt;
    }

    public void setUnlockedAt(LocalDateTime unlockedAt) {
        this.unlockedAt = unlockedAt;
    }
}
