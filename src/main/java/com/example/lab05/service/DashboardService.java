package com.example.lab05.service;

import com.example.lab05.dto.DashboardResponse;
import com.example.lab05.model.cassandra.SensorReading;
import com.example.lab05.model.elastic.ProductDocument;
import com.example.lab05.model.mongo.PurchaseReceipt;
import com.example.lab05.model.neo4j.Person;
import com.example.lab05.repository.mongo.PurchaseReceiptRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private static final Logger log =
            LoggerFactory.getLogger(DashboardService.class);

    private final PurchaseReceiptRepository purchaseReceiptRepository;
    private final SocialGraphService socialGraphService;
    private final SensorService sensorService;
    private final ProductSearchService productSearchService;
    private final RedisTemplate<String, Object> redisTemplate;

    public DashboardService(PurchaseReceiptRepository purchaseReceiptRepository,
                            SocialGraphService socialGraphService,
                            SensorService sensorService,
                            ProductSearchService productSearchService,
                            RedisTemplate<String, Object> redisTemplate) {
        this.purchaseReceiptRepository = purchaseReceiptRepository;
        this.socialGraphService = socialGraphService;
        this.sensorService = sensorService;
        this.productSearchService = productSearchService;
        this.redisTemplate = redisTemplate;
    }

    public DashboardResponse getDashboard(String personName) {

        // Step 0 — Redis (check cache, try-catch)
        String cacheKey = "dashboard:" + personName;
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached instanceof DashboardResponse cachedResponse) {
                return new DashboardResponse(
                        cachedResponse.personName(),
                        cachedResponse.totalSpent(),
                        cachedResponse.purchaseCount(),
                        cachedResponse.recentPurchases(),
                        cachedResponse.friendRecommendations(),
                        cachedResponse.friendsOfFriends(),
                        cachedResponse.recentActivity(),
                        cachedResponse.youMightAlsoLike(),
                        true
                );
            }
        } catch (Exception e) {
            log.warn("Redis cache check failed for {}: {}",
                    personName, e.getMessage());
        }

        // Step 1 — MongoDB
        List<PurchaseReceipt> receipts = purchaseReceiptRepository.findByPersonName(personName);

        Double totalSpent = receipts.stream()
                .map(PurchaseReceipt::getTotalPrice)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();

        Integer purchaseCount = receipts.size();

        List<PurchaseReceipt> recentPurchases = receipts.stream()
                .sorted(Comparator.comparing(
                        PurchaseReceipt::getPurchasedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .limit(5)
                .toList();

        // Step 2 — Neo4j (try-catch)
        List<Map<String, Object>> friendRecommendations = Collections.emptyList();
        List<String> friendsOfFriends = Collections.emptyList();

        try {
            friendRecommendations = socialGraphService.getRecommendations(personName, 5);
            friendsOfFriends = socialGraphService.getFriendsOfFriends(personName).stream()
                    .map(Person::getName)
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to fetch Neo4j data for {}: {}",
                    personName, e.getMessage());
        }

        // Step 3 — Cassandra (try-catch)
        List<SensorReading> recentActivity = Collections.emptyList();

        try {
            recentActivity = sensorService.getLatestReadings(
                    "user-activity-" + personName.toLowerCase(),
                    10
            );
        } catch (Exception e) {
            log.warn("Failed to fetch activity for {}: {}",
                    personName, e.getMessage());
        }

        // Step 4 — Elasticsearch (try-catch)
        List<String> youMightAlsoLike = Collections.emptyList();

        try {
            Set<String> purchasedNames = receipts.stream()
                    .map(PurchaseReceipt::getProductName)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            Set<String> categories = receipts.stream()
                    .map(PurchaseReceipt::getProductCategory)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            LinkedHashSet<String> suggestions = new LinkedHashSet<>();

            for (String category : categories) {
                List<String> perCategorySuggestions = productSearchService.getByCategory(category).stream()
                        .map(ProductDocument::getName)
                        .filter(Objects::nonNull)
                        .filter(name -> !purchasedNames.contains(name))
                        .limit(2)
                        .toList();

                suggestions.addAll(perCategorySuggestions);
            }

            youMightAlsoLike = new ArrayList<>(suggestions);
        } catch (Exception e) {
            log.warn("Failed to fetch ES suggestions for {}: {}",
                    personName, e.getMessage());
        }

        // Step 5 — Construct and cache
        DashboardResponse response = new DashboardResponse(
                personName,
                totalSpent,
                purchaseCount,
                recentPurchases,
                friendRecommendations,
                friendsOfFriends,
                recentActivity,
                youMightAlsoLike,
                false
        );

        try {
            redisTemplate.opsForValue().set(cacheKey, response, Duration.ofMinutes(5));
        } catch (Exception e) {
            log.warn("Failed to cache dashboard for {}: {}",
                    personName, e.getMessage());
        }

        return response;
    }
}