package org.dhis2.usescases.eventsWithoutRegistration.eventCapture;

import org.dhis2.commons.bindings.SdkExtensionsKt;
import org.dhis2.data.dhislogic.AuthoritiesKt;
import org.hisp.dhis.android.core.D2;
import org.hisp.dhis.android.core.common.BaseIdentifiableObject;
import org.hisp.dhis.android.core.common.ValidationStrategy;
import org.hisp.dhis.android.core.enrollment.Enrollment;
import org.hisp.dhis.android.core.enrollment.EnrollmentStatus;
import org.hisp.dhis.android.core.event.Event;
import org.hisp.dhis.android.core.event.EventEditableStatus;
import org.hisp.dhis.android.core.event.EventNonEditableReason;
import org.hisp.dhis.android.core.event.EventStatus;
import org.hisp.dhis.android.core.maintenance.D2Error;
import org.hisp.dhis.android.core.organisationunit.OrganisationUnit;
import org.hisp.dhis.android.core.program.ProgramRule;
import org.hisp.dhis.android.core.program.ProgramRuleAction;
import org.hisp.dhis.android.core.program.ProgramRuleActionType;
import org.hisp.dhis.android.core.settings.ProgramConfigurationSetting;

import java.util.Date;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.Single;
import timber.log.Timber;

public class EventCaptureRepositoryImpl implements EventCaptureContract.EventCaptureRepository {

    private final String eventUid;
    private final D2 d2;

    public EventCaptureRepositoryImpl(String eventUid, D2 d2) {
        this.eventUid = Objects.requireNonNull(eventUid, "eventUid cannot be null");
        this.d2 = Objects.requireNonNull(d2, "D2 instance cannot be null");
    }

    private Event getCurrentEvent() {
        try {
            return d2.eventModule().events().uid(eventUid).blockingGet();
        } catch (Exception e) {
            Timber.e(e, "Failed to get current event");
            throw new RuntimeException("Failed to load event data");
        }
    }

    @Override
    public boolean isEnrollmentOpen() {
        try {
            Event currentEvent = getCurrentEvent();
            return currentEvent.enrollment() == null ||
                    d2.enrollmentModule().enrollmentService().blockingIsOpen(currentEvent.enrollment());
        } catch (Exception e) {
            Timber.e(e, "Error checking enrollment status");
            return false;
        }
    }

    @Override
    public boolean isEnrollmentCancelled() {
        try {
            String enrollmentUid = getCurrentEvent().enrollment();
            if (enrollmentUid == null) return false;

            Enrollment enrollment = d2.enrollmentModule().enrollments().uid(enrollmentUid).blockingGet();
            return enrollment != null && enrollment.status() == EnrollmentStatus.CANCELLED;
        } catch (Exception e) {
            Timber.e(e, "Error checking enrollment cancellation status");
            return false;
        }
    }

    @Override
    public boolean isEventEditable(String eventUid) {
        try {
            return d2.eventModule().eventService().blockingIsEditable(eventUid);
        } catch (Exception e) {
            Timber.e(e, "Error checking event editability");
            return false;
        }
    }

    @Override
    public Flowable<String> programStageName() {
        return d2.programModule().programStages()
                .uid(getCurrentEvent().programStage())
                .get()
                .map(BaseIdentifiableObject::displayName)
                .onErrorReturnItem("")
                .toFlowable();
    }

    @Override
    public Flowable<OrganisationUnit> orgUnit() {
        return Flowable.fromCallable(() -> {
            String orgUnitUid = getCurrentEvent().organisationUnit();
            OrganisationUnit orgUnit = d2.organisationUnitModule()
                    .organisationUnits()
                    .uid(orgUnitUid)
                    .blockingGet();
            if (orgUnit == null) {
                throw new RuntimeException("Organisation unit not found");
            }
            return orgUnit;
        }).onErrorReturnItem(OrganisationUnit.builder().uid("").displayName("Unknown").build());
    }

    @Override
    public Observable<Boolean> completeEvent() {
        return Observable.fromCallable(() -> {
            try {
                d2.eventModule().events().uid(eventUid).setStatus(EventStatus.COMPLETED);
                return true;
            } catch (D2Error d2Error) {
                Timber.e(d2Error);
                return false;
            }
        });
    }

    @Override
    public Observable<Boolean> deleteEvent() {
        return d2.eventModule().events().uid(eventUid).delete()
                .toObservable()
                .map(ignored -> true)
                .onErrorReturnItem(false);
    }


    @Override
    public Observable<Boolean> updateEventStatus(EventStatus status) {
        return Observable.fromCallable(() -> {
            try {
                d2.eventModule().events().uid(eventUid).setStatus(status);
                return true;
            } catch (Exception e) {
                Timber.e(e);
                return false;
            }
        });
    }

    @Override
    public Observable<Boolean> rescheduleEvent(Date newDate) {
        return Observable.fromCallable(() -> {
            try {
                d2.eventModule().events().uid(eventUid).setDueDate(newDate);
                d2.eventModule().events().uid(eventUid).setStatus(EventStatus.SCHEDULE);
                return true;
            } catch (Exception e) {
                Timber.e(e);
                return false;
            }
        });
    }

    @Override
    public Observable<String> programStage() {
        return Observable.fromCallable(() -> {
            String programStage = getCurrentEvent().programStage();
            if (programStage == null) {
                throw new RuntimeException("Program stage not found");
            }
            return programStage;
        }).onErrorReturnItem("");
    }

    @Override
    public boolean getAccessDataWrite() {
        try {
            return d2.eventModule().eventService().blockingHasDataWriteAccess(eventUid);
        } catch (Exception e) {
            Timber.e(e);
            return false;
        }
    }

    public Single<EventEditableStatus> getEditableStatus(String eventUid) {
        return Single.fromCallable(() -> {
            try {
                return d2.eventModule().eventService().blockingGetEditableStatus(eventUid);
            } catch (Exception e) {
                Timber.e(e);
                return new EventEditableStatus.NonEditable(EventNonEditableReason.EXPIRED);

            }
        });
    }

    @Override
    public Flowable<EventStatus> eventStatus() {
        return Flowable.fromCallable(() -> {
            EventStatus status = getCurrentEvent().status();
            if (status == null) {
                throw new RuntimeException("Event status not found");
            }
            return status;
        }).onErrorReturnItem(EventStatus.ACTIVE);
    }

    @Override
    public Single<Boolean> canReOpenEvent() {
        return Single.fromCallable(() -> {
            try {
                return d2.userModule().authorities()
                        .byName()
                        .in(AuthoritiesKt.AUTH_UNCOMPLETE_EVENT, AuthoritiesKt.AUTH_ALL)
                        .one()
                        .blockingExists();
            } catch (Exception e) {
                Timber.e(e);
                return false;
            }
        });
    }

    @Override
    public Observable<Boolean> isCompletedEventExpired(String eventUid) {
        return d2.eventModule().eventService()
                .getEditableStatus(eventUid)
                .map(editionStatus -> {
                    if (editionStatus instanceof EventEditableStatus.NonEditable) {
                        return ((EventEditableStatus.NonEditable) editionStatus)
                                .getReason() == EventNonEditableReason.EXPIRED;
                    }
                    return false;
                })
                .onErrorReturnItem(false)
                .toObservable();
    }

    @Override
    public Flowable<Boolean> eventIntegrityCheck() {
        return Flowable.fromCallable(() -> {
            Event event = getCurrentEvent();
            return (event.status() == EventStatus.COMPLETED ||
                    event.status() == EventStatus.ACTIVE ||
                    event.status() == EventStatus.SKIPPED) &&
                    (event.eventDate() == null || !event.eventDate().after(new Date()));
        }).onErrorReturnItem(false);
    }

    @Override
    public Single<Integer> getNoteCount() {
        return d2.noteModule().notes()
                .byEventUid().eq(eventUid)
                .count()
                .onErrorReturnItem(0);
    }

    @Override
    public boolean showCompletionPercentage() {
        try {
            if (!d2.settingModule().appearanceSettings().blockingExists()) {
                return true;
            }

            ProgramConfigurationSetting setting = d2.settingModule()
                    .appearanceSettings()
                    .getProgramConfigurationByUid(getCurrentEvent().program());

            return setting == null ||
                    setting.completionSpinner() == null ||
                    setting.completionSpinner();
        } catch (Exception e) {
            Timber.e(e);
            return true;
        }
    }

    @Override
    public boolean hasAnalytics() {
        try {
            Event currentEvent = getCurrentEvent();
            boolean hasProgramIndicators = d2.programModule().programIndicators()
                    .byProgramUid().eq(currentEvent.program())
                    .blockingGet().size() > 0;

            List<ProgramRule> programRules = d2.programModule().programRules()
                    .withProgramRuleActions()
                    .byProgramUid().eq(currentEvent.program())
                    .blockingGet();

            for (ProgramRule rule : programRules) {
                if (rule.programRuleActions() != null) {
                    for (ProgramRuleAction action : rule.programRuleActions()) {
                        if (action.programRuleActionType() == ProgramRuleActionType.DISPLAYKEYVALUEPAIR ||
                                action.programRuleActionType() == ProgramRuleActionType.DISPLAYTEXT) {
                            return true;
                        }
                    }
                }
            }

            return hasProgramIndicators;
        } catch (Exception e) {
            Timber.e(e);
            return false;
        }
    }

    @Override
    public boolean hasRelationships() {
        try {
            return d2.relationshipModule().relationshipTypes()
                    .byAvailableForEvent(eventUid)
                    .blockingGet().size() > 0;
        } catch (Exception e) {
            Timber.e(e);
            return false;
        }
    }

    @Override
    public ValidationStrategy validationStrategy() {
        try {
            ValidationStrategy strategy = SdkExtensionsKt.programStage(d2, programStage().blockingFirst())
                    .validationStrategy();
            return strategy != null ? strategy : ValidationStrategy.ON_COMPLETE;
        } catch (Exception e) {
            Timber.e(e);
            return ValidationStrategy.ON_COMPLETE;
        }
    }

    @Override
    @Nullable
    public String getEnrollmentUid() {
        try {
            return getCurrentEvent().enrollment();
        } catch (Exception e) {
            Timber.e(e);
            return null;
        }
    }

    @Override
    @Nullable
    public String getTeiUid() {
        try {
            String enrollmentUid = getEnrollmentUid();
            if (enrollmentUid == null) return null;

            Enrollment enrollment = d2.enrollmentModule().enrollments()
                    .uid(enrollmentUid)
                    .blockingGet();
            return enrollment != null ? enrollment.trackedEntityInstance() : null;
        } catch (Exception e) {
            Timber.e(e);
            return null;
        }
    }
}