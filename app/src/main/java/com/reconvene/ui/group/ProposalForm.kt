package com.reconvene.ui.group

import com.reconvene.data.model.SessionProposal
import com.reconvene.ui.create.CreatePodForm
import com.reconvene.ui.create.toSingleLine
import java.time.ZoneId

// A proposal becomes a pod, so it's picked with the create form's when, where and vibe fields. The
// form's topic, course and size aren't used: the pod takes the group's name and room for everyone.
fun CreatePodForm.toProposal(id: String, byUid: String, zone: ZoneId) = SessionProposal(
    id = id,
    startsAt = startsAt(zone),
    endsAt = endsAt(zone),
    building = building.orEmpty().toSingleLine().trim(),
    meetingPoint = meetingPoint.toSingleLine().trim(),
    mode = mode,
    byUid = byUid,
)
