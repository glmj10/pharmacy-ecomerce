package com.pharmacy.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "homepage")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HomePage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "banner_2")
    private String banner2;

    @Column(name = "banner_3")
    private String banner3;

    @Column(name = "category_1_banner")
    private String category1Banner;

    @Column(name = "category_1_id")
    private Integer category1Id;

    @Column(name = "category_1_title")
    private String category1Title;

    @Column(name = "category_2_banner")
    private String category2Banner;

    @Column(name = "category_2_id")
    private Integer category2Id;

    @Column(name = "category_2_title")
    private String category2Title;

    @Column(name = "most_searches", columnDefinition = "TEXT")
    private String mostSearches;

    @Column(name = "top_banner", columnDefinition = "TEXT")
    private String topBanner;
}
