package io.jitrapon.glom.board.item.event

import com.google.android.gms.maps.model.LatLng
import io.jitrapon.glom.base.model.AndroidString
import io.jitrapon.glom.base.model.ButtonUiModel
import io.jitrapon.glom.base.model.UiModel

data class EventItemDetailUiModel(val name: Pair<AndroidString, Boolean>,
                                  val startDate: AndroidString?,
                                  val endDate: AndroidString?,
                                  val location: EventLocation?,
                                  val locationName: AndroidString?,
                                  val locationDescription: AndroidString?,
                                  val locationLatLng: LatLng?,
                                  val attendeeTitle: AndroidString?,
                                  val attendees: List<UserUiModel>,
                                  val attendButton: ButtonUiModel,
                                  val note: AndroidString?,
                                  val planButton: ButtonUiModel,
                                  val source: EventSourceUiModel,
                                  override var status: UiModel.Status = UiModel.Status.SUCCESS) : UiModel
