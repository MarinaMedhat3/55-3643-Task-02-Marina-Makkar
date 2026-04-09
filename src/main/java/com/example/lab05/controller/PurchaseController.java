package com.example.lab05.controller;

import com.example.lab05.dto.PurchaseRequest;
import com.example.lab05.model.mongo.PurchaseReceipt;
import com.example.lab05.service.PurchaseService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/55-3643/purchases")
public class PurchaseController {

    private final PurchaseService purchaseService;

    public PurchaseController(PurchaseService purchaseService) {
        this.purchaseService = purchaseService;
    }

    @PostMapping
    public PurchaseReceipt executePurchase(@RequestBody PurchaseRequest request) {
        return purchaseService.executePurchase(request);
    }

    @GetMapping("/person/{personName}")
    public List<PurchaseReceipt> getByPersonName(@PathVariable String personName) {
        return purchaseService.getReceiptsByPerson(personName);
    }
}