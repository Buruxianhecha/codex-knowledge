#!/usr/bin/env python3
"""Small v0.38.55 feature: Todo + opens text/gallery/camera actions; image is optional.

Do NOT redesign Todo screen, wallpaper, glass palette or existing cards.
All photos are stored in the app-owned ImageStore and included in portable backups.
"""
from pathlib import Path
import sys

def apply(root:Path):
    here=Path(__file__).resolve().parent
    src=root/"app/src/main/java/com/cleo/cleos"
    tests=root/"app/src/test/java/com/cleo/cleos"
    gradle=root/"app/build.gradle.kts"
    before=gradle.read_text(encoding="utf-8")
    assert before.count('versionName = "0.38.54"')==1
    assert before.count('versionCode = 62120')==1

    def edit(name, pairs):
        path=src/name
        old=path.read_text(encoding="utf-8")
        new=old
        for key, value in pairs:
            count=new.count(key)
            if count!=1:
                raise RuntimeError(f"{name}: expected 1 occurrence, got {count}: {key[:130]!r}")
            new=new.replace(key,value,1)
        path.write_text(new,encoding="utf-8")

    edit("data/db/Entities.kt", [
        ('    val title: String,\n    val note: String = "",\n    val done: Boolean = false,\n    /** LocalDate.toEpochDay(),',
         '    val title: String,\n    val note: String = "",\n    /** Optional app-owned photo file, absent from all older records and backups. */\n    val imageFile: String? = null,\n    val done: Boolean = false,\n    /** LocalDate.toEpochDay(),')
    ])
    edit("data/db/AppDatabase.kt",[
        ('    version = 23,','    version = 24,'),
        ('    abstract fun groupMembers(): ConversationMemberDao',
         '    abstract fun groupMembers(): ConversationMemberDao'),
        ('''    }

    abstract fun groupMembers(): ConversationMemberDao''',
         '''        /** Existing todos remain intact; older rows simply have no photo. */
        val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE todos ADD COLUMN imageFile TEXT")
            }
        }
    }

    abstract fun groupMembers(): ConversationMemberDao''')
    ])
    edit("CleosApp.kt",[
        ("AppDatabase.MIGRATION_22_23)",
         "AppDatabase.MIGRATION_22_23, AppDatabase.MIGRATION_23_24)")
    ])
    edit("data/BackupService.kt",[
        ('        val pictures = (data.diary.flatMap',
         '''        val pictures = (data.todos.mapNotNull { it.imageFile } +
            data.diary.flatMap'''),
        ('                    db.todos().insertAll(d.todos)',
         '''                    // Never allow a stale or crafted media name to escape app storage.
                    db.todos().insertAll(d.todos.map { it.copy(imageFile = picture(it.imageFile)) })''')
    ])

    # Do not replace the current Todo UI with a reimplementation. Patch only its
    # existing + button, draft creation, preview and optional picture thumbnail.
    edit("ui/todo/TodoScreen.kt",[
        ('@Composable\nfun TodoTab', '@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nfun TodoTab'),
        ('package com.cleo.cleos.ui.todo\n',
         '''package com.cleo.cleos.ui.todo

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import coil3.compose.AsyncImage
import java.io.File
'''),
        ('import com.cleo.cleos.data.db.TodoEntity',
         '''import com.cleo.cleos.data.db.TodoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext'''),
        ('''    fun add(title: String) {''',
         '''    /** Photos are copied and downscaled to private app storage before editing. */
    suspend fun importPhoto(uri: Uri): String = c.images.import(uri, maxEdge=1920, prefix="todo_").file

    suspend fun capturePhoto(bitmap: Bitmap): String {
        val filename=c.images.save(bitmap,prefix="todo_")
        bitmap.recycle()
        return filename
    }

    fun photoFile(name: String?): File? {
        if(name == null || !name.matches(Regex("""todo_[a-fA-F0-9-]{36}\.(jpg|png)"""))) return null
        return c.images.file(name).takeIf { it.isFile }
    }

    fun discardPhoto(name: String?) {
        if(photoFile(name) != null) c.appScope.launch {
            if(c.db.todos().all().none { it.imageFile == name }) c.images.delete(listOfNotNull(name))
        }
    }

    fun add(title: String) {'''),
        ('''    var editing by remember { mutableStateOf<TodoEntity?>(null) }
    var barHeight''',
         '''    var editing by remember { mutableStateOf<TodoEntity?>(null) }
    var addMenuOpen by remember { mutableStateOf(false) }
    var importingPhoto by remember { mutableStateOf(false) }
    var barHeight'''),
        ('''    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()''',
         '''    fun openPhotoDraft(photo: String) {
        // Title is still mandatory; choosing an image does not silently create a todo.
        editing = TodoEntity(title=input.trim(),createdAt=System.currentTimeMillis(),
            imageFile=photo)
        input=""
    }
    val gallery = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if(uri!=null) scope.launch {
            importingPhoto=true
            try { openPhotoDraft(vm.importPhoto(uri)) }
            catch (e:Exception) { snackbar.showSnackbar("图片添加失败："+(e.message ?: "无法读取")) }
            finally { importingPhoto=false }
        }
    }
    val camera = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if(bitmap!=null) scope.launch {
            importingPhoto=true
            try { openPhotoDraft(vm.capturePhoto(bitmap)) }
            catch (e:Exception) { snackbar.showSnackbar("照片保存失败："+(e.message ?: "无法保存")) }
            finally { importingPhoto=false }
        }
    }

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()'''),
        ('''    fun deleteWithUndo(todo: TodoEntity) {
        vm.delete(todo)''',
         '''    fun deleteWithUndo(todo: TodoEntity) {
        vm.delete(todo)'''),
        ('''            if (result == SnackbarResult.ActionPerformed) vm.restore(todo)''',
         '''            if (result == SnackbarResult.ActionPerformed) vm.restore(todo)
            else vm.discardPhoto(todo.imageFile)'''),
        ('''                    onAdd = {
                        vm.add(input)
                        input = ""
                    },''',
         '''                    onAdd = { addMenuOpen=true },
                    onSubmit = {
                        if(input.isNotBlank()) {
                            vm.add(input)
                            input=""
                        }
                    },'''),
        ('''                    onLongClick = { deleteWithUndo(todo) },
                    modifier = Modifier.animateItem(),''',
         '''                    onLongClick = { deleteWithUndo(todo) },
                    image = vm.photoFile(todo.imageFile),
                    modifier = Modifier.animateItem(),'''),
        ('''                            onLongClick = { deleteWithUndo(todo) },
                            modifier = Modifier.animateItem(),''',
         '''                            onLongClick = { deleteWithUndo(todo) },
                            image = vm.photoFile(todo.imageFile),
                            modifier = Modifier.animateItem(),'''),
        ('''    editing?.let { todo ->
        TodoEditDialog(
            todo = todo,
            onDismiss = { editing = null },''',
         '''    if(addMenuOpen) ModalBottomSheet(
        onDismissRequest={ addMenuOpen=false },
        sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=12.dp)) {
            Text("新建待办",fontWeight=FontWeight.SemiBold,fontSize=18.sp,
                modifier=Modifier.padding(bottom=16.dp))
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                TextButton(onClick={
                    addMenuOpen=false
                    editing=TodoEntity(title=input.trim(),createdAt=System.currentTimeMillis())
                    input=""
                },modifier=Modifier.weight(1f)) {
                    Icon(Icons.Rounded.EditNote,null)
                    Spacer(Modifier.width(5.dp))
                    Text("文字待办")
                }
                TextButton(onClick={
                    addMenuOpen=false
                    gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },modifier=Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Image,null)
                    Spacer(Modifier.width(5.dp))
                    Text("相册")
                }
                TextButton(onClick={
                    addMenuOpen=false
                    camera.launch(null)
                },modifier=Modifier.weight(1f)) {
                    Icon(Icons.Rounded.PhotoCamera,null)
                    Spacer(Modifier.width(5.dp))
                    Text("拍摄")
                }
            }
            Spacer(Modifier.size(12.dp))
        }
    }
    editing?.let { todo ->
        TodoEditDialog(
            todo = todo,
            image = vm.photoFile(todo.imageFile),
            onDismiss = {
                if(todo.id==0L) vm.discardPhoto(todo.imageFile)
                editing = null
            },'''),
        ('''            onDelete = {
                editing = null
                deleteWithUndo(todo)
            },''',
         '''            onDelete = {
                editing = null
                if(todo.id==0L) vm.discardPhoto(todo.imageFile)
                else deleteWithUndo(todo)
            },'''),
        ('''    todo: TodoEntity,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,''',
         '''    todo: TodoEntity,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    image: File? = null,
    modifier: Modifier = Modifier,'''),
        ('''        Row(verticalAlignment = Alignment.CenterVertically) {
            CheckCircle(todo.done, onToggle)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {''',
         '''        Row(verticalAlignment = Alignment.CenterVertically) {
            if (image != null) {
                AsyncImage(model=image,contentDescription="待办图片",
                    contentScale=ContentScale.Crop,
                    modifier=Modifier.size(66.dp).clip(RoundedCornerShape(13.dp)))
            } else {
                CheckCircle(todo.done, onToggle)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {'''),
        ('''                }
            }
        }
    }
}

@Composable
private fun CheckCircle''',
         '''                }
            }
            if (image != null) {
                Spacer(Modifier.width(8.dp))
                CheckCircle(todo.done, onToggle)
            }
        }
    }
}

@Composable
private fun CheckCircle'''),
        ('''private fun AddBar(backdrop: Backdrop, text: String, onTextChange: (String) -> Unit, onAdd: () -> Unit) {''',
         '''private fun AddBar(backdrop: Backdrop, text: String, onTextChange: (String) -> Unit,
    onAdd: () -> Unit, onSubmit: () -> Unit) {'''),
        ('''keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onAdd() })''',
         '''keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onSubmit() })'''),
        ('''            "添加",
            onAdd,''',
         '''            "添加选项",
            onAdd,'''),
        ('''            enabled = text.isNotBlank(),
            size = 50.dp,''',
         '''            enabled = true,
            size = 50.dp,''')
    ])

    edit("ui/todo/TodoEditDialog.kt",[
        ('package com.cleo.cleos.ui.todo\n',
         '''package com.cleo.cleos.ui.todo

import coil3.compose.AsyncImage
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import java.io.File
'''),
        ('''    todo: TodoEntity,
    onDismiss: () -> Unit,''',
         '''    todo: TodoEntity,
    image: File? = null,
    onDismiss: () -> Unit,'''),
        ('''            Column {
                OutlinedTextField(''',
         '''            Column {
                if(image!=null) {
                    AsyncImage(model=image, contentDescription="待办附图预览",
                        contentScale=ContentScale.Crop,
                        modifier=Modifier.fillMaxWidth().height(148.dp).clip(RoundedCornerShape(14.dp)))
                    Spacer(Modifier.height(10.dp))
                }
                OutlinedTextField(''')
    ])

    test=here/"TodoPhotoFieldTest_03855.kt"
    assert test.is_file()
    (tests/"data").mkdir(parents=True,exist_ok=True)
    (tests/"data/TodoPhotoFieldTest.kt").write_bytes(test.read_bytes())

    # Guard: do not turn the Todo screen into a whole new design.
    screen=(src/"ui/todo/TodoScreen.kt").read_text(encoding="utf-8")
    assert 'GlassPage(' in screen and 'GlassSurface(' in screen
    assert 'ModalBottomSheet' in screen and 'PickVisualMedia' in screen
    assert 'TakePicturePreview' in screen and 'TodoRow(' in screen
    assert 'enabled = true,' in screen and 'CheckCircle(todo.done, onToggle)' in screen

    gradle.write_text(before.replace('versionName = "0.38.54"',
        'versionName = "0.38.55"').replace('versionCode = 62120',
        'versionCode = 62121'),encoding="utf-8")
    print("0.38.55 Build 62121: Todo + menu (text/gallery/camera), optional image card and safe migration")

if __name__=="__main__":
    if len(sys.argv)!=2:raise SystemExit("usage: apply_todo_photo_plus_03855.py PROJECT_ROOT")
    apply(Path(sys.argv[1]).resolve())
