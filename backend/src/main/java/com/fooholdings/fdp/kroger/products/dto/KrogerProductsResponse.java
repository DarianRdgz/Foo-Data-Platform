package com.fooholdings.fdp.kroger.products.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class KrogerProductsResponse {

    private List<Product> data;

    public List<Product> getData() { return data; }
    public void setData(List<Product> data) { this.data = data; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Product {
        private String productId;
        private String upc;
        private String description;
        private List<Item> items;

        public String getProductId() { return productId; }
        public void setProductId(String productId) { this.productId = productId; }

        public String getUpc() { return upc; }
        public void setUpc(String upc) { this.upc = upc; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public List<Item> getItems() { return items; }
        public void setItems(List<Item> items) { this.items = items; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        private Price price;

        public Price getPrice() { return price; }
        public void setPrice(Price price) { this.price = price; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Price {
        private Double regular;
        private Double promo;

        public Double getRegular() { return regular; }
        public void setRegular(Double regular) { this.regular = regular; }

        public Double getPromo() { return promo; }
        public void setPromo(Double promo) { this.promo = promo; }
    }
}
