package com.ubertob.fotf.exercises.chapter6

import com.ubertob.fotf.exercises.chapter6.ElevatorState.DoorsOpenAtFloor
import com.ubertob.fotf.exercises.chapter6.ElevatorState.TravelingAtFloor

sealed class ElevatorCommand {
    data class CallElevator(val floor: Int) : ElevatorCommand()
    data class GoToFloor(val floor: Int) : ElevatorCommand()
}

sealed class ElevatorState {
    data class DoorsOpenAtFloor(val floor: Int) : ElevatorState()
    data class TravelingAtFloor(val floor: Int) : ElevatorState()
}

fun handleCommand(state: ElevatorState, command: ElevatorCommand): ElevatorState {
    return when (command) {
        is ElevatorCommand.CallElevator -> {
            when (state) {
                is DoorsOpenAtFloor -> state // 문이 열려있다면 아무 것도 할 필요가 없다
                is TravelingAtFloor -> DoorsOpenAtFloor(command.floor) // 이동중이라면 호출한 플로우의 문을 연다
            }
        }

        is ElevatorCommand.GoToFloor -> {
            when (state) {
                is DoorsOpenAtFloor -> TravelingAtFloor(command.floor) // 문이 열려 있다면 아무 것도 할 필요가 없다
                is TravelingAtFloor -> state // 이동중이라면 호출한 플로우의 문을 연다
            }
        }
    }
}