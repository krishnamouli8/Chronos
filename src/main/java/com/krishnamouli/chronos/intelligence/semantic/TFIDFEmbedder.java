package com.krishnamouli.chronos.intelligence.semantic;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight TF-IDF text embedder for semantic similarity.
 * Uses term frequency and inverse document frequency for vector representation.
 */
public class TFIDFEmbedder {
    private final Map<String, Double> idfScores;
    private final int maxVocabSize;
    private final Map<String, Integer> termFrequencies;
    private int documentCount;

    public TFIDFEmbedder(int maxVocabSize) {
        this.maxVocabSize = maxVocabSize;
        this.idfScores = new ConcurrentHashMap<>();
        this.termFrequencies = new ConcurrentHashMap<>();
        this.documentCount = 0;
    }

    public Map<String, Double> embed(String text) {
        List<String> tokens = tokenize(text);
        Map<String, Integer> termCounts = countTerms(tokens);

        // Calculate TF-IDF vector
        Map<String, Double> vector = new HashMap<>();
        for (Map.Entry<String, Integer> entry : termCounts.entrySet()) {
            String term = entry.getKey();
            int count = entry.getValue();

            double tf = (double) count / tokens.size(); // Term frequency
            double idf = idfScores.getOrDefault(term, 1.0); // Inverse document frequency
            vector.put(term, tf * idf);
        }

        // L2 normalization for cosine similarity
        return normalize(vector);
    }

    public void updateCorpus(String text) {
        List<String> tokens = tokenize(text);
        Set<String> uniqueTerms = new HashSet<>(tokens);

        // Update term occurrences
        for (String term : uniqueTerms) {
            termFrequencies.merge(term, 1, Integer::sum);
        }

        documentCount++;

        // Recalculate IDF scores
        for (Map.Entry<String, Integer> entry : termFrequencies.entrySet()) {
            String term = entry.getKey();
            int docFreq = entry.getValue();
            double idf = Math.log((double) documentCount / docFreq);
            idfScores.put(term, idf);
        }

        // Prune vocabulary if needed
        if (termFrequencies.size() > maxVocabSize) {
            pruneVocabulary();
        }
    }

    public static double cosineSimilarity(Map<String, Double> vec1, Map<String, Double> vec2) {
        if (vec1.isEmpty() || vec2.isEmpty()) {
            return 0.0;
        }

        double dotProduct = 0.0;
        for (Map.Entry<String, Double> entry : vec1.entrySet()) {
            String term = entry.getKey();
            if (vec2.containsKey(term)) {
                dotProduct += entry.getValue() * vec2.get(term);
            }
        }

        return dotProduct; // Already normalized, so this is cosine similarity
    }

    private List<String> tokenize(String text) {
        // Simple tokenization: lowercase, split on non-alphanumeric, remove short
        // tokens
        return Arrays.stream(text.toLowerCase().split("[^a-z0-9]+"))
                .filter(token -> token.length() > 2) // Remove very short tokens
                .toList();
    }

    private Map<String, Integer> countTerms(List<String> tokens) {
        Map<String, Integer> counts = new HashMap<>();
        for (String token : tokens) {
            counts.merge(token, 1, Integer::sum);
        }
        return counts;
    }

    private Map<String, Double> normalize(Map<String, Double> vector) {
        double norm = 0.0;
        for (double value : vector.values()) {
            norm += value * value;
        }
        norm = Math.sqrt(norm);

        if (norm == 0.0) {
            return vector;
        }

        Map<String, Double> normalized = new HashMap<>();
        for (Map.Entry<String, Double> entry : vector.entrySet()) {
            normalized.put(entry.getKey(), entry.getValue() / norm);
        }
        return normalized;
    }

    private void pruneVocabulary() {
        // Keep only top N most frequent terms
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(termFrequencies.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        Set<String> toKeep = new HashSet<>();
        for (int i = 0; i < maxVocabSize && i < sorted.size(); i++) {
            toKeep.add(sorted.get(i).getKey());
        }

        termFrequencies.keySet().retainAll(toKeep);
        idfScores.keySet().retainAll(toKeep);
    }
}
