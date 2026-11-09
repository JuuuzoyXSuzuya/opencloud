package dev.opencloud.region;

final class Profile {
    private final String code;
    private final String numeric;
    private final String operator;
    private final String country;

    Profile(String iso, String numericCode, String network, String countryName) {
        this.code = iso;
        this.numeric = numericCode;
        this.operator = network;
        this.country = countryName;
    }

    String code() {
        return code;
    }

    String numeric() {
        return numeric;
    }

    String operator() {
        return operator;
    }

    String country() {
        return country;
    }

    String caption() {
        return country + " - " + operator;
    }
}
