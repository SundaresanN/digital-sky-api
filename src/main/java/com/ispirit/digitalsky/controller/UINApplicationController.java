package com.ispirit.digitalsky.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ispirit.digitalsky.document.UINApplication;
import com.ispirit.digitalsky.domain.ApplicationStatus;
import com.ispirit.digitalsky.domain.ApproveRequestBody;
import com.ispirit.digitalsky.domain.UserPrincipal;
import com.ispirit.digitalsky.dto.Errors;
import com.ispirit.digitalsky.exception.ApplicationNotFoundException;
import com.ispirit.digitalsky.exception.ApplicationNotInSubmittedStatus;
import com.ispirit.digitalsky.exception.StorageFileNotFoundException;
import com.ispirit.digitalsky.exception.UnAuthorizedAccessException;
import com.ispirit.digitalsky.service.api.UINApplicationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static com.ispirit.digitalsky.controller.UINApplicationController.UIN_APPLICATION_RESOURCE_BASE_PATH;
import static com.ispirit.digitalsky.util.FileStoreHelper.resolveFileName;

@RestController
@RequestMapping(UIN_APPLICATION_RESOURCE_BASE_PATH)
public class UINApplicationController {

    public static final String UIN_APPLICATION_RESOURCE_BASE_PATH = "/api/applicationForm/uinApplication";

    private UINApplicationService uinApplicationService;

    @Autowired
    public UINApplicationController(UINApplicationService uinApplicationService) {

        this.uinApplicationService = uinApplicationService;
    }

    @RequestMapping(method = RequestMethod.POST, consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createApplication(
            @RequestParam(value = "importPermissionDoc", required = false) MultipartFile importPermissionDoc,
            @RequestParam(value = "cinDoc", required = false) MultipartFile cinDoc,
            @RequestParam(value = "gstinDoc", required = false) MultipartFile gstinDoc,
            @RequestParam(value = "panCardDoc", required = false) MultipartFile panCardDoc,
            @RequestParam(value = "dotPermissionDoc", required = false) MultipartFile dotPermissionDoc,
            @RequestParam(value = "securityClearanceDoc", required = false) MultipartFile securityClearanceDoc,
            @RequestParam(value = "etaDoc", required = false) MultipartFile etaDoc,
            @RequestParam(value = "opManualDoc", required = false) MultipartFile opManualDoc,
            @RequestParam(value = "maintenanceGuidelinesDoc", required = false) MultipartFile maintenanceGuidelinesDoc,
            @RequestParam(value = "uinApplication") String uinApplicationString) {

        try {
            ObjectMapper mapper = new ObjectMapper();
            UINApplication uinApplication = mapper.readValue(uinApplicationString, UINApplication.class);
            appendDocs(uinApplication, importPermissionDoc, cinDoc, gstinDoc, panCardDoc, securityClearanceDoc, dotPermissionDoc,etaDoc,opManualDoc,maintenanceGuidelinesDoc);
            UINApplication createdForm = uinApplicationService.createApplication(uinApplication);
            return new ResponseEntity<>(createdForm, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(new Errors(e.getMessage()), HttpStatus.CONFLICT);
        }
    }


    @RequestMapping(value = "/{id}", method = RequestMethod.PATCH, consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateApplication(
            @PathVariable String id,
            @RequestParam(value = "importPermissionDoc", required = false) MultipartFile importPermissionDoc,
            @RequestParam(value = "cinDoc", required = false) MultipartFile cinDoc,
            @RequestParam(value = "gstinDoc", required = false) MultipartFile gstinDoc,
            @RequestParam(value = "panCardDoc", required = false) MultipartFile panCardDoc,
            @RequestParam(value = "securityClearanceDoc", required = false) MultipartFile securityClearanceDoc,
            @RequestParam(value = "dotPermissionDoc", required = false) MultipartFile dotPermissionDoc,
            @RequestParam(value = "etaDoc", required = false) MultipartFile etaDoc,
            @RequestParam(value = "opManualDoc", required = false) MultipartFile opManualDoc,
            @RequestParam(value = "maintenanceGuidelinesDoc", required = false) MultipartFile maintenanceGuidelinesDoc,
            @RequestParam(value = "uinApplication") String uinApplicationString) {

        try {
            UserPrincipal userPrincipal = UserPrincipal.securityContext();
            UINApplication application = uinApplicationService.get(id);
            if (userPrincipal.getId() != application.getApplicantId()) {
                return new ResponseEntity<>(new Errors("UnAuthorized Access"), HttpStatus.UNAUTHORIZED);
            }

            if (!application.canBeModified()) {
                return new ResponseEntity<>(new Errors("Application not in draft status, cannot be modified"), HttpStatus.UNPROCESSABLE_ENTITY);
            }

            ObjectMapper mapper = new ObjectMapper();
            UINApplication uinApplication = mapper.readValue(uinApplicationString, UINApplication.class);
            appendDocs(uinApplication, importPermissionDoc, cinDoc, gstinDoc, panCardDoc, securityClearanceDoc, dotPermissionDoc,etaDoc,opManualDoc,maintenanceGuidelinesDoc);
            UINApplication updatedForm = uinApplicationService.updateApplication(id, uinApplication);
            return new ResponseEntity<>(updatedForm, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(new Errors(e.getMessage()), HttpStatus.CONFLICT);
        }
    }


    @RequestMapping(value = "/approve/{id}", method = RequestMethod.PATCH, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> approveApplication(@PathVariable String id, @RequestBody ApproveRequestBody approveRequestBody) {
        try {
            UINApplication updatedForm = uinApplicationService.approveApplication(approveRequestBody);
            return new ResponseEntity<>(updatedForm, HttpStatus.OK);
        } catch (ApplicationNotFoundException e) {
            return new ResponseEntity<>(new Errors(e.getMessage()), HttpStatus.NOT_FOUND);
        } catch (UnAuthorizedAccessException e) {
            return new ResponseEntity<>(new Errors(e.getMessage()), HttpStatus.UNAUTHORIZED);
        }catch (ApplicationNotInSubmittedStatus e) {
            return new ResponseEntity<>(new Errors(e.getMessage()), HttpStatus.BAD_REQUEST);
        }
    }

    @RequestMapping(value = "/list", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> listApplications() {
        UserPrincipal userPrincipal = UserPrincipal.securityContext();
        Collection<?> applicationForms = uinApplicationService.getApplicationsOfApplicant(userPrincipal.getId());
        return new ResponseEntity<>(applicationForms, HttpStatus.OK);
    }

    @RequestMapping(value = "/getAll", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> listAll() {

        Collection<?> applicationForms = uinApplicationService.getAllApplications();
        List<?> submittedApplications = applicationForms.stream().filter(applicationForm -> {
            ApplicationStatus status = ((UINApplication) applicationForm).getStatus();
            return status != null && status != ApplicationStatus.DRAFT;
        }).collect(Collectors.toList());

        return new ResponseEntity<>(submittedApplications, HttpStatus.OK);
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getApplication(@PathVariable String id) {

        UINApplication applicationForm = uinApplicationService.get(id);

        UserPrincipal userPrincipal = UserPrincipal.securityContext();
        if (userPrincipal.getId() != applicationForm.getApplicantId()) {
            return new ResponseEntity<>(new Errors("UnAuthorized Access"), HttpStatus.UNAUTHORIZED);
        }
        return new ResponseEntity<>(applicationForm, HttpStatus.OK);
    }

    @RequestMapping(value = "/{applicationId}/document/{fileName:.+}", method = RequestMethod.GET)
    public ResponseEntity<?> getFile(@PathVariable String applicationId, @PathVariable String fileName) {

        try {
            UINApplication applicationForm = uinApplicationService.get(applicationId);

            UserPrincipal userPrincipal = UserPrincipal.securityContext();
            if (!userPrincipal.isAdmin() && userPrincipal.getId() != applicationForm.getApplicantId()) {
                return new ResponseEntity<>(new Errors("UnAuthorized Access"), HttpStatus.UNAUTHORIZED);
            }
            Resource resourceFile = uinApplicationService.getFile(applicationId, fileName);
            return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + resourceFile.getFilename() + "\"").body(resourceFile);
        } catch (StorageFileNotFoundException e) {
            return new ResponseEntity<>(new Errors(e.getMessage()), HttpStatus.NOT_FOUND);
        }
    }

    private void appendDocs(UINApplication uinApplication, MultipartFile importPermissionDoc, MultipartFile cinDoc, MultipartFile gstinDoc, MultipartFile panCardDoc,
                            MultipartFile securityClearanceDoc, MultipartFile dotPermissionDoc, MultipartFile etaDoc, MultipartFile opManualDoc, MultipartFile maintenanceGuidelinesDoc) {

        uinApplication.setImportPermissionDoc(importPermissionDoc);
        uinApplication.setImportPermissionDocName(resolveFileName(importPermissionDoc));

        uinApplication.setCinDoc(cinDoc);
        uinApplication.setCinDocName(resolveFileName(cinDoc));

        uinApplication.setGstinDoc(gstinDoc);
        uinApplication.setGstinDocName(resolveFileName(gstinDoc));

        uinApplication.setPanCardDoc(panCardDoc);
        uinApplication.setPanCardDocName(resolveFileName(panCardDoc));

        uinApplication.setSecurityClearanceDoc(securityClearanceDoc);
        uinApplication.setSecurityClearanceDocName(resolveFileName(securityClearanceDoc));

        uinApplication.setDotPermissionDoc(dotPermissionDoc);
        uinApplication.setDotPermissionDocName(resolveFileName(dotPermissionDoc));

        uinApplication.setEtaDoc(etaDoc);
        uinApplication.setEtaDocName(resolveFileName(etaDoc));

        uinApplication.setOpManualDoc(opManualDoc);
        uinApplication.setOpManualDocName(resolveFileName(opManualDoc));

        uinApplication.setMaintenanceGuidelinesDoc(maintenanceGuidelinesDoc);
        uinApplication.setMaintenanceGuidelinesDocName(resolveFileName(maintenanceGuidelinesDoc));

    }


}
