package com.fooholdings.fdp.kroger.locations.dto;

import java.util.List;

public class KrogerLocationResponse {
    private List<Location> data;

    public List<Location> getData() { return data; }
    public void setData(List<Location> data) { this.data = data; }

    public static class Location {
        private String locationId;
        private String name;
        private Address address;

        public String getLocationId() { return locationId; }
        public void setLocationId(String locationId) { this.locationId = locationId; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public Address getAddress() { return address; }
        public void setAddress(Address address) { this.address = address; }
    }

    public static class Address {
        private String addressLine1;
        private String city;
        private String state;
        private String zipCode;

        public String getAddressLine1() { return addressLine1; }
        public void setAddressLine1(String addressLine1) { this.addressLine1 = addressLine1; }

        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }

        public String getState() { return state; }
        public void setState(String state) { this.state = state; }

        public String getZipCode() { return zipCode; }
        public void setZipCode(String zipCode) { this.zipCode = zipCode; }
    }
}
