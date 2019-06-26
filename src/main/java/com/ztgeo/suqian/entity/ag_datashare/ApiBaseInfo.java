package com.ztgeo.suqian.entity.ag_datashare;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import java.io.Serializable;
import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity(name = "api_base_info")
public class ApiBaseInfo implements Serializable {
    @Id
    @Column(name = "api_id")
    private String apiId;
    @Column(name = "api_pubkey")
    private String apiPubkey;
    @Column(name = "api_name")
    private String apiName;
    @Column(name = "base_url")
    private String baseUrl;
    @Column(name = "path")
    private String path;
    @Column(name = "method")
    private String method;
    @Column(name = "enabled_status")
    private Integer enabledStatus;
    @Column(name = "responsible_person_name")
    private String responsiblePersonName;
    @Column(name = "responsible_person_tel")
    private String responsiblePersonTel;
    @Column(name = "api_owner_id")
    private String apiOwnerId;
    @Column(name = "api_owner_name")
    private String apiOwnerName;
    @Column(name = "crt_user_id")
    private String crtUserId;
    @Column(name = "crt_time")
    private Date crtTime;
    @Column(name = "upd_user_id")
    private String updUserId;
    @Column(name = "upd_time")
    private Date updTime;
}
