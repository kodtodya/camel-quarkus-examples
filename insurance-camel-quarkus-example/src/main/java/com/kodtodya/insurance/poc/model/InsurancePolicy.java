package com.kodtodya.insurance.poc.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Represents an insurance policy record from the database.
 * Used as the primary data transfer object throughout the Camel pipeline.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class InsurancePolicy {

    private Long id;
    private String policyNumber;
    private String policyType;
    private String status;

    // Holder information
    private String holderFirstName;
    private String holderLastName;
    private String holderEmail;
    private String holderPhone;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate holderDateOfBirth;
    private String holderGender;

    // Nominee
    private String nomineeName;
    private String nomineeRelationship;

    // Financial
    private BigDecimal insuredAmount;
    private BigDecimal premiumAmount;
    private String premiumFrequency;

    // Policy dates
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate renewalDate;

    // Metadata
    private String riskCategory;
    private String agentCode;
    private String branchCode;
    private String underwriterCode;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    // Processing flags
    private Boolean processed;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime processedAt;
    private String processingBatchId;
    private String remarks;

    // -----------------------------------------------
    // Constructors
    // -----------------------------------------------
    public InsurancePolicy() {}

    // -----------------------------------------------
    // Getters & Setters
    // -----------------------------------------------
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPolicyNumber() { return policyNumber; }
    public void setPolicyNumber(String policyNumber) { this.policyNumber = policyNumber; }

    public String getPolicyType() { return policyType; }
    public void setPolicyType(String policyType) { this.policyType = policyType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getHolderFirstName() { return holderFirstName; }
    public void setHolderFirstName(String holderFirstName) { this.holderFirstName = holderFirstName; }

    public String getHolderLastName() { return holderLastName; }
    public void setHolderLastName(String holderLastName) { this.holderLastName = holderLastName; }

    public String getHolderEmail() { return holderEmail; }
    public void setHolderEmail(String holderEmail) { this.holderEmail = holderEmail; }

    public String getHolderPhone() { return holderPhone; }
    public void setHolderPhone(String holderPhone) { this.holderPhone = holderPhone; }

    public LocalDate getHolderDateOfBirth() { return holderDateOfBirth; }
    public void setHolderDateOfBirth(LocalDate holderDateOfBirth) { this.holderDateOfBirth = holderDateOfBirth; }

    public String getHolderGender() { return holderGender; }
    public void setHolderGender(String holderGender) { this.holderGender = holderGender; }

    public String getNomineeName() { return nomineeName; }
    public void setNomineeName(String nomineeName) { this.nomineeName = nomineeName; }

    public String getNomineeRelationship() { return nomineeRelationship; }
    public void setNomineeRelationship(String nomineeRelationship) { this.nomineeRelationship = nomineeRelationship; }

    public BigDecimal getInsuredAmount() { return insuredAmount; }
    public void setInsuredAmount(BigDecimal insuredAmount) { this.insuredAmount = insuredAmount; }

    public BigDecimal getPremiumAmount() { return premiumAmount; }
    public void setPremiumAmount(BigDecimal premiumAmount) { this.premiumAmount = premiumAmount; }

    public String getPremiumFrequency() { return premiumFrequency; }
    public void setPremiumFrequency(String premiumFrequency) { this.premiumFrequency = premiumFrequency; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public LocalDate getRenewalDate() { return renewalDate; }
    public void setRenewalDate(LocalDate renewalDate) { this.renewalDate = renewalDate; }

    public String getRiskCategory() { return riskCategory; }
    public void setRiskCategory(String riskCategory) { this.riskCategory = riskCategory; }

    public String getAgentCode() { return agentCode; }
    public void setAgentCode(String agentCode) { this.agentCode = agentCode; }

    public String getBranchCode() { return branchCode; }
    public void setBranchCode(String branchCode) { this.branchCode = branchCode; }

    public String getUnderwriterCode() { return underwriterCode; }
    public void setUnderwriterCode(String underwriterCode) { this.underwriterCode = underwriterCode; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Boolean getProcessed() { return processed; }
    public void setProcessed(Boolean processed) { this.processed = processed; }

    public LocalDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(LocalDateTime processedAt) { this.processedAt = processedAt; }

    public String getProcessingBatchId() { return processingBatchId; }
    public void setProcessingBatchId(String processingBatchId) { this.processingBatchId = processingBatchId; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }

    @Override
    public String toString() {
        return "InsurancePolicy{id=" + id + ", policyNumber='" + policyNumber + "', type='" + policyType +
               "', status='" + status + "', holder='" + holderFirstName + " " + holderLastName + "'}";
    }
}
