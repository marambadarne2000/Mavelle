package com.maramigo.catalog;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Sort;
import java.util.List;
/** @author Maram Abbas */
@RestController @RequestMapping("/api/companies")
public class CompanyController {
    private final CompanyRepository companies;
    public CompanyController(CompanyRepository companies){this.companies=companies;}
    public record View(Long id,String slug,String name,String description,String monogram,boolean demo){}
    @GetMapping public List<View> list(){return companies.findAll(Sort.by("id")).stream().filter(c->"APPROVED".equals(c.getStatus())).map(c->new View(c.getId(),c.getSlug(),c.getName(),c.getDescription(),c.getMonogram(),c.isDemo())).toList();}
}
