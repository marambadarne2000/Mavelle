package com.maramigo.catalog;
import jakarta.persistence.*;

/** Fictional demonstration stores. @author Maram Abbas */
@Entity @Table(name="companies")
public class Company {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    private java.util.UUID logoId;
    public java.util.UUID getLogoId(){return logoId;}
    private String slug;
    private String name;
    private String description;
    private String monogram;
    private boolean demo;
    private String status;
    private String sourceUrl;
    public String getSourceUrl(){return sourceUrl;}
    public String getStatus(){return status;}
    protected Company() {}
    public Long getId(){return id;}
    public String getSlug(){return slug;}
    public String getName(){return name;}
    public String getDescription(){return description;}
    public String getMonogram(){return monogram;}
    public boolean isDemo(){return demo;}
}
