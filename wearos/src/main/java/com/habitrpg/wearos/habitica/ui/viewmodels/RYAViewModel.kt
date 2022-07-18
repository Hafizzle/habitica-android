package com.habitrpg.wearos.habitica.ui.viewmodels

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.habitrpg.common.habitica.models.responses.TaskDirection
import com.habitrpg.common.habitica.models.tasks.TaskType
import com.habitrpg.wearos.habitica.data.repositories.TaskRepository
import com.habitrpg.wearos.habitica.data.repositories.UserRepository
import com.habitrpg.wearos.habitica.managers.AppStateManager
import com.habitrpg.wearos.habitica.models.tasks.Task
import com.habitrpg.wearos.habitica.util.ExceptionHandlerBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RYAViewModel @Inject constructor(
    userRepository: UserRepository,
    taskRepository: TaskRepository,
    exceptionBuilder: ExceptionHandlerBuilder, appStateManager: AppStateManager
) : BaseViewModel(userRepository, taskRepository, exceptionBuilder, appStateManager) {
    val tasks = MutableLiveData<List<Task>>()

    private val tasksToComplete = mutableListOf<Task>()

    init {
        viewModelScope.launch(exceptionBuilder.silent()) {
            val taskList: List<Task> = taskRepository.getTasks(TaskType.DAILY)
                .map { it.filter { task -> task.isDue == true && !task.completed } }
                .first()
            tasks.value = taskList
        }
    }

    fun tappedTask(task: Task) {
        task.completed = !task.completed
        tasks.value = tasks.value
        if (task.completed) {
            if (!tasksToComplete.contains(task)) {
                tasksToComplete.add(task)
            }
        } else {
            if (tasksToComplete.contains(task)) {
                tasksToComplete.remove(task)
            }
        }
    }

    fun runCron(function: (Boolean) -> Unit) {
        viewModelScope.launch(exceptionBuilder.userFacing(this)) {
            appStateManager.startLoading()
            for (task in tasksToComplete) {
                taskRepository.scoreTask(null, task, TaskDirection.UP)
            }
            userRepository.runCron()
            val user = userRepository.retrieveUser(true)
            taskRepository.retrieveTasks(user?.tasksOrder, true)
            function(true)
            appStateManager.endLoading()
        }
    }
}
