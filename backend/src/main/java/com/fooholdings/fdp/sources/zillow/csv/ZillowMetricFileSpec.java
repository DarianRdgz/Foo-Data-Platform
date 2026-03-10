package com.fooholdings.fdp.sources.zillow.csv;

public enum ZillowMetricFileSpec {

// ZHVI
ZHVI_STATE("ZHVI/State_zhvi_uc_sfrcondo_tier_0.33_0.67_sm_sa_month.csv", "housing.home_value", "ZillowZhviAdapter"),
ZHVI_METRO("ZHVI/Metro_zhvi_uc_sfrcondo_tier_0.33_0.67_sm_sa_month.csv", "housing.home_value", "ZillowZhviAdapter"),
ZHVI_COUNTY("ZHVI/County_zhvi_uc_sfrcondo_tier_0.33_0.67_sm_sa_month.csv", "housing.home_value", "ZillowZhviAdapter"),
ZHVI_CITY("ZHVI/City_zhvi_uc_sfrcondo_tier_0.33_0.67_sm_sa_month.csv", "housing.home_value", "ZillowZhviAdapter"),
ZHVI_ZIP("ZHVI/Zip_zhvi_uc_sfrcondo_tier_0.33_0.67_sm_sa_month.csv", "housing.home_value", "ZillowZhviAdapter"),

// ZORI
ZORI_METRO("ZORI/Metro_zori_uc_sfrcondomfr_sm_month.csv", "housing.rent_index", "ZillowZoriAdapter"),
ZORI_COUNTY("ZORI/County_zori_uc_sfrcondomfr_sm_month.csv", "housing.rent_index", "ZillowZoriAdapter"),
ZORI_CITY("ZORI/City_zori_uc_sfrcondomfr_sm_month.csv", "housing.rent_index", "ZillowZoriAdapter"),
ZORI_ZIP("ZORI/Zip_zori_uc_sfrcondomfr_sm_month.csv", "housing.rent_index", "ZillowZoriAdapter"),

// Listings
LISTINGS_INVENTORY_METRO("FOR-SALE-LISTINGS/Metro_invt_fs_uc_sfrcondo_sm_month.csv", "housing.for_sale_inventory", "ZillowListingsAdapter"),
LISTINGS_MEDIAN_LIST_PRICE_METRO("FOR-SALE-LISTINGS/Metro_mlp_uc_sfrcondo_sm_month.csv", "housing.median_list_price", "ZillowListingsAdapter"),
LISTINGS_NEW_LISTINGS_METRO("FOR-SALE-LISTINGS/Metro_new_listings_uc_sfrcondo_sm_month.csv", "housing.new_listings", "ZillowListingsAdapter"),
LISTINGS_NEW_PENDING_METRO("FOR-SALE-LISTINGS/Metro_new_pending_uc_sfrcondo_month.csv", "housing.newly_pending", "ZillowListingsAdapter"),
SALES_MEDIAN_SALE_PRICE_METRO("SALES/Metro_median_sale_price_now_uc_sfrcondo_month.csv", "housing.median_sale_price", "ZillowListingsAdapter"),
SALES_COUNT_METRO("SALES/Metro_sales_count_now_uc_sfrcondo_month.csv", "housing.sales_count", "ZillowListingsAdapter"),
DAYS_TO_PENDING_METRO("DAYS-ON-MARKET-AND-PRICE-CUTS/Metro_mean_doz_pending_uc_sfrcondo_sm_month.csv", "housing.days_to_pending", "ZillowListingsAdapter"),
PRICE_CUT_SHARE_METRO("DAYS-ON-MARKET-AND-PRICE-CUTS/Metro_perc_listings_price_cut_uc_sfrcondo_sm_month.csv", "housing.share_with_price_cut", "ZillowListingsAdapter"),
MARKET_HEAT_METRO("MARKET-HEAT-INDEX/Metro_market_temp_index_uc_sfrcondo_month.csv", "housing.market_heat_index", "ZillowListingsAdapter"),

// Affordability
AFFORDABILITY_INCOME_NEEDED_METRO("AFFORDABILITY/Metro_new_homeowner_income_needed_downpayment_0.20_uc_sfrcondo_tier_0.33_0.67_sm_sa_month.csv", "housing.affordability.income_needed", "ZillowAffordabilityAdapter"),
AFFORDABILITY_RATIO_METRO("AFFORDABILITY/Metro_new_homeowner_affordability_downpayment_0.20_uc_sfrcondo_tier_0.33_0.67_sm_sa_month.csv", "housing.affordability.ratio", "ZillowAffordabilityAdapter"),
AFFORDABILITY_YEARS_TO_SAVE_METRO("AFFORDABILITY/Metro_years_to_save_downpayment_0.20_uc_sfrcondo_tier_0.33_0.67_sm_sa_month.csv", "housing.affordability.years_to_save", "ZillowAffordabilityAdapter");

    private final String relativePath;
    private final String category;
    private final String sourceName;

    ZillowMetricFileSpec(String relativePath, String category, String sourceName) {
        this.relativePath = relativePath;
        this.category = category;
        this.sourceName = sourceName;
    }

    public String relativePath() {
        return relativePath;
    }

    public String category() {
        return category;
    }

    public String sourceName() {
        return sourceName;
    }
}