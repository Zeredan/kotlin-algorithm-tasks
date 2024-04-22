// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.Color.Companion.Black
import androidx.compose.ui.graphics.Color.Companion.Blue
import androidx.compose.ui.graphics.Color.Companion.Cyan
import androidx.compose.ui.graphics.Color.Companion.DarkGray
import androidx.compose.ui.graphics.Color.Companion.Green
import androidx.compose.ui.graphics.Color.Companion.LightGray
import androidx.compose.ui.graphics.Color.Companion.Magenta
import androidx.compose.ui.graphics.Color.Companion.Red
import androidx.compose.ui.graphics.Color.Companion.White
import androidx.compose.ui.graphics.Color.Companion.Yellow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jetbrains.skiko.currentNanoTime
import java.io.*
import java.net.URI
import java.nio.file.Paths
import java.util.Deque
import java.util.Stack
import java.util.function.Consumer
import java.util.stream.Stream
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.random.Random
import kotlin.system.measureNanoTime
import kotlin.system.measureTimeMillis

val List<*>.lastIndex: Int
    get() {return this.size - 1}


data class Container<T>(var value: T)
fun<T> T.toContainer() = Container(this)
//3 2 1 5 4
//2 1 3 4 5
//1 2 3 4 5
inline fun <reified E> MutableList<E>.ShakerSorted(): MutableList<E> where E: Int {
    return (0 to this.size - 1).toContainer().run leftright@{ while(this.value.first < this.value.second){
        (this.value.first..this.value.second - 1).forEach {i ->
            if (this@ShakerSorted[i] > this@ShakerSorted[i + 1])
            {
                this@ShakerSorted[i] = this@ShakerSorted[i + 1].also{this@ShakerSorted[i + 1] = this@ShakerSorted[i]}
            }
        }
        (this.value.first + 1..this.value.second - 1).reversed().forEach {i ->
            if (this@ShakerSorted[i] < this@ShakerSorted[i - 1])
            {
                this@ShakerSorted[i] = this@ShakerSorted[i - 1].also{this@ShakerSorted[i - 1] = this@ShakerSorted[i]}
            }
        }
        this.value = this.value.first + 1 to this.value.second - 1
    } }.let{this}
}
//5 1 3 2 4
//2 1 3 5 4
fun <E> MutableList<E>.QuickSorted(): MutableList<E> where E: Comparable<E> {
    fun MutableList<E>.InnerQSort(left: Int = 0, right: Int = this.size - 1)
    {
        var mid = (left + right) / 2
        (left to right).toContainer().run leftright@
        {
            while(this.value.first < this.value.second)
            {
                this.value = left to right
                while(this@InnerQSort[this.value.first] <= this@InnerQSort[mid] && this.value.first < mid) this.value = this.value.first + 1 to this.value.second
                while(this@InnerQSort[this.value.second] >= this@InnerQSort[mid] && this.value.second > mid) this.value = this.value.first to this.value.second - 1
                this@InnerQSort[this.value.first] = this@InnerQSort[this.value.second].also{this@InnerQSort[this.value.second] = this@InnerQSort[this.value.first]}
            }
            if (left < mid) this@InnerQSort.InnerQSort(left, mid)
            if (mid + 1 < right) this@InnerQSort.InnerQSort(mid + 1, right)
        }
    }
    return this.InnerQSort().let{this}
}
//2 1 3 9 7
//2 7 1 9 3
//2 1 7 9 3
//2 1 3 9 7

//. . . . 2 4 1 5 3 . . .
//. . . . 2 1 3 4 5 . . .
fun<E> MutableList<E>.QuickSorted1(comp: Comparator<in E>): MutableList<E> where E: Comparable<E> {

    fun MutableList<E>.InnerQSort(left: Int = 0, right: Int = this.lastIndex)
    {
        var pivotInd = (Math.random() * 1000).toInt() % (right + 1 - left) + left
        val pivot = this[pivotInd]
        var i = left - 1
        (left..right).forEach { j ->
            if (comp.compare(this[j], pivot) < 0){
                ++i
                if (this[i] == pivot) pivotInd = j
                this[i] = this[j].also{this[j] = this[i]}
            }
        }
        ++i
        this[i] = this[pivotInd].also{this[pivotInd] = this[i]}
        if ((i - 1) - left > 0) this.InnerQSort(left, i - 1)
        if (right - (i + 1) > 0) this.InnerQSort(i + 1, right)
    }
    return this.InnerQSort().let{this}
}
//REGION MAINUI
fun Modifier.twoColoredTemperatureBg(sideColor: Color, midColor: Color) : Modifier
{
    return this.background(object : ShaderBrush()
    {
        override fun createShader(size: Size): Shader {
            return LinearGradientShader(Offset(0f, 0f), Offset(size.width, 0f), listOf(sideColor, midColor, sideColor))
        }
    })
}

@Composable
fun GradientalBox(modifier: Modifier, centerColor: Color = Color.Yellow, sideColor: Color = Color.Yellow, content: @Composable () -> Unit)
{
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .then(modifier)
            .clip(CutCornerShape(10f))
            .background(
                object : ShaderBrush(){
                    override fun createShader(size: Size): Shader {
                        return RadialGradientShader(Offset(size.width / 2, size.height / 2), size.width / 3, listOf(White, centerColor, White, White, sideColor, Black), listOf(0f, 0.3f, 0.4f, 0.45f, 0.95f, 1f))
                    }
                }
            )
    )
    {
        content()
    }
}
//ENDREGION MAINCOMPOSABLES

//REGION MENUPAGES
@Composable
fun InfoPage()
{
    Column(
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
    )
    {
        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.weight(1f))
        Text("Сборник работ по Алгоритмам и анализу сложности")
        Spacer(modifier = Modifier.weight(3f))
        Text("Сгибнев Владимир Сергеевич 6203-020302D")
        Spacer(modifier = Modifier.weight(1f))

        Row(){Text("Практика 1 -> Вариант "); Text("3", color = Color.Green) }
        Spacer(modifier = Modifier.height(10.dp))
        Row(){Text("Лабораторная 1 -> Вариант "); Text("1", color = Color.Green) }

        Spacer(modifier = Modifier.height(30.dp))

        Row(){Text("Практика 2 -> Вариант "); Text("3", color = Color.Green) }
        Spacer(modifier = Modifier.height(10.dp))
        Row(){Text("Лабораторная 2 -> Вариант "); Text("-", color = Color.Green) }

        Spacer(modifier = Modifier.height(30.dp))

        Row(){Text("Практика 3 -> Вариант "); Text("?", color = Color.Green) }
        Spacer(modifier = Modifier.height(10.dp))
        Row(){Text("Лабораторная 3 -> Вариант "); Text("1", color = Color.Green) }


        Spacer(modifier = Modifier.weight(1f))
    }
}
@OptIn(ExperimentalGraphicsApi::class)
@Composable
fun Practise_1()
{
    @Composable fun Practise_1_input()
    {
        var composeRefresh = remember{ mutableStateOf(false) }
        var inputText = remember{ mutableStateOf("") }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
        )
        {
            Spacer(modifier = Modifier.weight(2f))
            TextField(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .twoColoredTemperatureBg(Yellow, Red),
                value = inputText.value,
                onValueChange = {
                    if (it.isEmpty()) inputText.value = it
                    it.firstOrNull { char -> ((char != ' ') && !char.isDigit()) } ?: let{_ -> inputText.value = it}
                },
                label = {
                    Text("Введите последовательность чисел")
                }
            )
            Spacer(modifier = Modifier.weight(1f))
            OutlinedButton(
                onClick = {
                    composeRefresh.value = !composeRefresh.value
                }
            ){
                Text("ОБНОВИТЬ")
            }
            Spacer(modifier = Modifier.weight(1f))
            repeat(2)
            {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                )
                {
                    var selectedSortMethod = remember { mutableStateOf("Не выбрано") }
                    var sortedResult = remember { mutableStateOf(mutableListOf<Int>() to 0f) }

                    var expanded = remember{mutableStateOf(false)}
                    LaunchedEffect(selectedSortMethod.value, inputText.value, composeRefresh.value)
                    {
                        sortedResult.value =
                            mutableListOf<Int>().toContainer().let { cont ->
                                measureNanoTime {
                                    cont.value = (Data.sortMethods[selectedSortMethod.value] ?: { a -> a }).invoke(
                                        inputText.value.trim().split(" ").map { it.toIntOrNull() ?: 0 }.toMutableList()
                                    )
                                }.run{
                                    cont.value to this.toFloat() / 10000000
                                }
                            }
                    }

                    Spacer(modifier = Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .background(Color.Cyan)
                            .pointerInput(Unit)
                            {
                                detectTapGestures {
                                    expanded.value = true
                                }
                            }
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    )
                    {
                        Text("Метод сортировки: " + selectedSortMethod.value)
                    }
                    DropdownMenu(
                        modifier = Modifier.background(Color.Black),
                        expanded = expanded.value,
                        onDismissRequest = {
                            expanded.value = false
                        }
                    )
                    {
                        Data.sortMethods.entries.forEach { sortMethod ->
                            DropdownMenuItem(
                                modifier = Modifier
                                    .twoColoredTemperatureBg(Cyan, Blue),
                                onClick =
                                {
                                    selectedSortMethod.value = sortMethod.key
                                    expanded.value = false
                                }
                            )
                            {
                                Text(sortMethod.key)
                            }
                            Divider(modifier = Modifier.height(20.dp))
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Column(

                    )
                    {
                        Box(
                            modifier = Modifier
                                .background(object : ShaderBrush() {
                                    override fun createShader(size: Size): Shader {
                                        return LinearGradientShader(
                                            Offset(0f, 0f),
                                            Offset(size.width, size.height),
                                            listOf(Color.Green, Color.Cyan, Color.Green)
                                        )
                                    }
                                })
                        )
                        {
                            Text("Отсортированный массив: " + sortedResult.value.first.joinToString(" "))
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Box(
                            modifier = Modifier
                                .background(object : ShaderBrush() {
                                    override fun createShader(size: Size): Shader {
                                        return LinearGradientShader(
                                            Offset(0f, 0f),
                                            Offset(size.width, size.height),
                                            listOf(Color.Magenta, Color.Red, Color.Magenta)
                                        )
                                    }
                                })
                        )
                        {
                            Text("Время сортировки: ${sortedResult.value.second.toString()}")
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(40.dp))
            }
            Spacer(modifier = Modifier.weight(0.26f))
        }
    }

    @Composable
    fun Practise_1_bigNumbers()
    {
        var composeRefresh = remember { mutableStateOf(false) }
        var numberMenuExpanded = remember { mutableStateOf(false) }
        var numberDiapasonExpanded = remember { mutableStateOf(false) }
        var numbersCount = remember { mutableStateOf("количество не выбрано") }
        var numberDiapason = remember { mutableStateOf("диапазон не выбран") }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
        )
        {
            Spacer(modifier = Modifier.weight(2f))
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
            )
            {
                Divider(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .background(Color.Magenta)
                        .clickable {
                            numberMenuExpanded.value = true
                        }
                        .padding(20.dp)
                )
                {
                    Text(numbersCount.value)
                }
                DropdownMenu(
                    modifier = Modifier.background(Color.Black),
                    expanded = numberMenuExpanded.value,
                    onDismissRequest = {
                        numberMenuExpanded.value = false
                    }
                )
                {
                    listOf("100", "1000", "10000", "100000").forEach { diap ->
                        DropdownMenuItem(
                            modifier = Modifier
                                .twoColoredTemperatureBg(Blue, Cyan)
                                .clickable {
                                    numbersCount.value = diap
                                    numberMenuExpanded.value = false
                                }
                                .padding(20.dp),
                            onClick = {
                                numbersCount.value = diap
                                numberMenuExpanded.value = false
                            }

                        )
                        {
                            Text(diap)
                        }
                        Divider(modifier = Modifier.height(20.dp))
                    }
                }
                Divider(modifier = Modifier.weight(1f))
                OutlinedButton(
                    onClick = {
                        composeRefresh.value = !composeRefresh.value
                    }
                ){
                    Text("ОБНОВИТЬ")
                }
                Divider(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .background(Color.Magenta)
                        .clickable {
                            numberDiapasonExpanded.value = true
                        }
                        .padding(20.dp)
                )
                {
                    Text(numberDiapason.value)
                }
                DropdownMenu(
                    modifier = Modifier.background(Color.Black),
                    expanded = numberDiapasonExpanded.value,
                    onDismissRequest = {
                        numberDiapasonExpanded.value = false
                    }
                )
                {
                    listOf("0-10", "10-100", "100-1000", "1000-10000").forEach { diap ->
                        DropdownMenuItem(
                            modifier = Modifier
                                .twoColoredTemperatureBg(Blue, Cyan)
                                .clickable {
                                    numberDiapason.value = diap
                                    numberDiapasonExpanded.value = false
                                }
                                .padding(20.dp),
                            onClick = {
                                numberDiapason.value = diap
                                numberDiapasonExpanded.value = false
                            }
                        )
                        {
                            Text(diap)
                        }
                        Divider(modifier = Modifier.height(20.dp))
                    }
                }
                Divider(modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.weight(1f))
            repeat(2)
            {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                )
                {
                    var selectedSortMethod = remember { mutableStateOf("Не выбрано") }
                    var sortedResult = remember { mutableStateOf<Long>(0) }

                    var expanded = remember{mutableStateOf(false)}
                    LaunchedEffect(selectedSortMethod.value, numberDiapason.value, numbersCount.value, composeRefresh.value)
                    {
                        sortedResult.value = try {
                            measureNanoTime {
                                (Data.sortMethods[selectedSortMethod.value] ?: { a -> a })(
                                    MutableList(
                                        numbersCount.value.toInt()
                                    ) {
                                        numberDiapason.value.split("-").run { this[0].toInt() to this[1].toInt() }
                                            .run { Random.nextInt(this.first, this.second) }
                                    })
                            } / 1000000
                        }
                        catch(e: Exception)
                        {
                            0
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .background(Color.Cyan)
                            .pointerInput(Unit)
                            {
                                detectTapGestures {
                                    expanded.value = true
                                }
                            }
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    )
                    {
                        Text("Метод сортировки: " + selectedSortMethod.value)
                    }
                    DropdownMenu(
                        modifier = Modifier.background(Color.Black),
                        expanded = expanded.value,
                        onDismissRequest = {
                            expanded.value = false
                        }
                    )
                    {
                        Data.sortMethods.entries.forEach { sortMethod ->
                            DropdownMenuItem(
                                modifier = Modifier
                                    .twoColoredTemperatureBg(Blue, Cyan),
                                onClick =
                                {
                                    selectedSortMethod.value = sortMethod.key
                                    expanded.value = false
                                }
                            )
                            {
                                Text(sortMethod.key)
                            }
                            Divider(modifier = Modifier.width(20.dp))
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.height(20.dp))
                    Box(
                        modifier = Modifier
                            .background(object : ShaderBrush() {
                                override fun createShader(size: Size): Shader {
                                    return LinearGradientShader(
                                        Offset(0f, 0f),
                                        Offset(size.width, size.height),
                                        listOf(Color.Magenta, Color.Red, Color.Magenta)
                                    )
                                }
                            })
                    )
                    {
                        Text("Время сортировки: ${sortedResult.value.toString()}")
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(40.dp))
            }
            Spacer(modifier = Modifier.weight(0.26f))
        }
    }

    var questions =  mutableMapOf<String, @Composable () -> Unit>(
    "Режим ввода массива" to {Practise_1_input()},
    "Режим с большими последовательностями" to {Practise_1_bigNumbers()}
    )

    var selectedOption = remember { mutableStateOf("Режим ввода массива") }
    Column(
        modifier = Modifier.fillMaxSize()
    )
    {
        TopAppBar(
            backgroundColor = Color.Magenta,
            elevation = 10.dp
        )
        {
            questions.forEach{ option ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(CutCornerShape(10.dp))
                        .clickable {
                            selectedOption.value = option.key
                        }
                        .background(if (option.key == selectedOption.value) Color.Green else Color.hsl(80f, 1f, 0.5f))
                        .padding(10.dp)
                )
                {
                    Text(option.key)
                }
                Spacer(modifier = Modifier.width(20.dp))
            }
        }
        questions[selectedOption.value]!!.invoke()
    }
}
@OptIn(ExperimentalGraphicsApi::class)
@Composable
fun Lab_1()
{
    open class MyLinkedList<T> : Iterable<T>, Cloneable
    {
        inner class MyNode<T>(var obj: T)
        {
            var next: MyNode<T>? = null
        }



        var headNode : MyNode<T>? = null

        var size: Int = 0
            get()
            {
                return (headNode?.let{1.toContainer().apply n@{
                    var currentNode = headNode!!
                    while(currentNode.next != null)
                    {
                        this.value++
                        currentNode = currentNode.next!!
                    }
                }.value} ?: 0)
            }

        operator fun get(ind: Int) : T
        {
            return headNode!!.toContainer().apply{repeat(ind){this.value = this.value.next!!} }.value.obj
        }

        operator fun set(ind : Int, value: T)
        {
            headNode!!.toContainer().apply{repeat(ind){this.value = this.value.next!!} }.value.obj = value
        }

        operator fun plusAssign(value: T)
        {
            if (this.headNode == null)
            {
                this.headNode = MyNode(value)
            }
            else this.getNode(this.size - 1).next = MyNode<T>(value)
        }

        public fun getNode(ind: Int): MyNode<T>
        {
            return headNode!!.toContainer().apply{repeat(ind){this.value = this.value.next!!} }.value
        }

        public override fun clone(): MyLinkedList<T> {
            return MyLinkedList<T>().apply{
                if (this@MyLinkedList.headNode != null)
                {
                    this.headNode = MyNode(this@MyLinkedList.headNode!!.obj)
                    var currentNode = this@MyLinkedList.headNode!!
                    var newCurrentNode = this.headNode!!
                    while(currentNode.next != null)
                    {
                        currentNode = currentNode.next!!
                        newCurrentNode.next = MyNode(currentNode.obj)
                        newCurrentNode = newCurrentNode.next!!
                    }
                }
            }
        }

        override fun iterator(): Iterator<T> = object : Iterator<T>{
            private var cursor = headNode
            override fun hasNext(): Boolean {
                return cursor != null
            }

            override fun next(): T {
                return (cursor?.obj ?: throw NoSuchElementException()).also{cursor = cursor?.next}
            }

        }
        constructor()
        {

        }
        constructor(vararg elems : T)
        {
            headNode = MyNode(elems[0])
            var currentNode = headNode
            elems.forEachIndexed { ind, elem ->
                if (ind > 0) {
                    currentNode!!.next = MyNode(elem)
                    currentNode = currentNode!!.next
                }
            }
        }
    }

    @Composable fun Task1()
    {
        var inputText = remember { mutableStateOf("") }
        var inputIndex = remember { mutableStateOf("") }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        )
        {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.Bottom
            )
            {
                TextField(
                    modifier = Modifier
                        .twoColoredTemperatureBg(Yellow, Red),
                    value = inputText.value,
                    placeholder = {
                        Text("Введите элементы списка")
                    },
                    onValueChange = { str ->
                        str.firstOrNull { !it.isDigit() && it != ' ' }?.let {}
                            ?: let {
                                inputText.value = str
                            }
                    }
                )
                Spacer(modifier = Modifier.width(40.dp))
                TextField(
                    modifier = Modifier
                        .twoColoredTemperatureBg(Blue, Green),
                    value = inputIndex.value,
                    placeholder = {
                        Text("Введите индекс элемента, на который будет ссылаться последний")
                    },
                    onValueChange = { str ->
                        if (str.isEmpty()) inputIndex.value = str
                        str.toIntOrNull()?.let { inputIndex.value = str }
                    }
                )
            }
            Divider(modifier = Modifier.fillMaxWidth().height(40.dp))
            Box(
                modifier = Modifier
                    .background(Color.Magenta)
                    .padding(10.dp)
            )
            {
                Text("Индекс начала узла: ${
                    try {
                        MyLinkedList<Pair<Number, Int>>(*inputText.value.split(" ").mapIndexed{ind, it -> it.toInt() to ind}.toTypedArray()).run lst@
                        {
                            this.getNode(this.size - 1).next = this.getNode(inputIndex.value.toInt())
                            var searchInd = 0
                            try {
                                this.forEach {
                                    if (it.second < searchInd) throw Exception(it.second.toString())
                                    ++searchInd
                                }
                            }
                            catch(e: Exception)
                            {
                                e.message!!.toInt()
                            }
                        }
                    }
                    catch(e: Exception)
                    {
                        -1
                    }
                }")
            }
        }
    }

    @Composable fun Task2()
    {
        var inputText = remember { mutableStateOf("") }
        var mainList = remember{ mutableStateOf(MyLinkedList<Number>()) }
        var clonedList = remember{ mutableStateOf(MyLinkedList<Number>()) }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        )
        {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.Bottom
            )
            {
                TextField(
                    modifier = Modifier
                        .twoColoredTemperatureBg(Yellow, Red),
                    value = inputText.value,
                    placeholder = {
                        Text("Введите элементы списка")
                    },
                    onValueChange = { str ->
                        str.firstOrNull { !it.isDigit() && it != ' ' }?.let {}
                            ?: let {
                                inputText.value = str
                            }
                    }
                )
                Spacer(modifier = Modifier.width(40.dp))
                Box(
                    modifier = Modifier
                        .background(Color.Cyan)
                        .clickable {
                            mainList.value = MyLinkedList(*inputText.value.split(" ").map{it.toInt()}.toTypedArray())
                            clonedList.value = mainList.value.clone()
                            mainList.value = MyLinkedList(*mainList.value.mapIndexed{ind, value -> value.toInt() + ind}.toTypedArray())
                        }
                        .padding(20.dp)
                )
                {
                    Text("Склонировать")
                }
            }
            Divider(modifier = Modifier.height(40.dp))
            Box(
                modifier = Modifier
                    .background(Color.Cyan)
                    .padding(20.dp)
            )
            {
                Text(mainList.value.joinToString("|"))
            }
            Divider(modifier = Modifier.height(40.dp))
            Box(
                modifier = Modifier
                    .background(Color.Cyan)
                    .padding(20.dp)
            )
            {
                Text(clonedList.value.joinToString("|"))
            }
        }
    }

    @Composable fun Task3()
    {
        var inputText = remember { mutableStateOf("") }
        var mainList = remember{ mutableStateOf(MyLinkedList<Number>()) }
        var clonedList = remember{ mutableStateOf(MyLinkedList<Number>()) }
        var deletedElements = remember { mutableStateOf(0) }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        )
        {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.Bottom
            )
            {
                TextField(
                    modifier = Modifier
                        .twoColoredTemperatureBg(Red, Yellow),
                    value = inputText.value,
                    placeholder = {
                        Text("Введите элементы списка")
                    },
                    onValueChange = { str ->
                        str.firstOrNull { !it.isDigit() && it != ' ' }?.let {}
                            ?: let {
                                inputText.value = str
                            }
                    }
                )
                Spacer(modifier = Modifier.width(40.dp))
                Box(
                    modifier = Modifier
                        .background(Color.Cyan)
                        .clickable {
                            clonedList.value = MyLinkedList()
                            mainList.value = MyLinkedList(*inputText.value.split(" ").map{it.toInt()}.toTypedArray())
                            deletedElements.value = mutableMapOf<Number, Int>().run unique@{
                                0.toContainer().apply countDel@{
                                    mainList.value.forEach { elem ->
                                        try {
                                            clonedList.value += elem.also{this@unique.putIfAbsent(elem, 1)?.let{throw Exception().apply{this@countDel.value++}}}
                                        }
                                        catch(e: Exception)
                                        {

                                        }
                                    }
                                }.value
                            }
                        }
                        .padding(20.dp)
                )
                {
                    Text("Удалить дубликаты")
                }
            }
            Divider(modifier = Modifier.height(40.dp))
            Box(
                modifier = Modifier
                    .background(Color.Cyan)
                    .padding(20.dp)
            )
            {
                Text(mainList.value.joinToString("|"))
            }
            Divider(modifier = Modifier.height(40.dp))
            Box(
                modifier = Modifier
                    .background(Color.Cyan)
                    .padding(20.dp)
            )
            {
                Text("Кол-во удаленных дубликатов: ${deletedElements.value}")
            }
            Divider(modifier = Modifier.height(40.dp))
            Box(
                modifier = Modifier
                    .background(Color.Cyan)
                    .padding(20.dp)
            )
            {
                Text(clonedList.value.joinToString("|"))
            }

        }
    }

    var selectedTask = remember{ mutableStateOf(0) }

    Column(
        modifier = Modifier.fillMaxSize()
    )
    {
        TopAppBar(
            backgroundColor = Color.Magenta,
            elevation = 10.dp
        )
        {
            listOf("Задача 1", "Задача 2", "задача 3").forEachIndexed{ind, it ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(CutCornerShape(10.dp))
                        .clickable {
                            selectedTask.value = ind
                        }
                        .background(if (ind == selectedTask.value) Color.Green else Color.hsl(80f, 1f, 0.5f))
                        .padding(10.dp)
                )
                {
                    Text(it)
                }
                Spacer(modifier = Modifier.width(20.dp))
            }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        )
        {
            listOf<@Composable () -> Unit>({Task1()}, {Task2()}, {Task3()})[selectedTask.value]()
        }
    }
}

@Composable
fun Practise_2()
{
    fun myMax(a: Int, b: Int): Int
    {
        val sgnA = (a.shr(31) and 1) xor 1
        val sgnB = (b.shr(31) and 1) xor 1
        val sgnAsubB = ((a - b).shr(31) and 1) xor 1 // reliable only if a and b the same sgn
        var sgnBsubA = sgnAsubB xor 1
        // if sgnA == sgnB we can substract(a-b) and if sgn(a-b)==1 return a else b
        // if sgnA != sngB we must check the sgn(a): if it == 1 return a else b
        val differentSign = (sgnA xor sgnB)
        val sameSign = differentSign xor 1
        println("\n\nsgnA: $sgnA\nsgnB: $sgnB\nsgnAsubB: $sgnAsubB\nsgnBsubA: $sgnBsubA\ndif: $differentSign\nsame: $sameSign")
        return sameSign * (sgnAsubB * a + sgnBsubA * b) + differentSign * (sgnA * a + sgnB * b)
    }

    var a = remember { mutableStateOf("") }
    var b = remember { mutableStateOf("") }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    )
    {
        Row()
        {
            Text("A: ")
            TextField(
                value = a.value,
                onValueChange = {str ->
                    if (str.isEmpty()) a.value = str
                    if (str in listOf("-", "+")) a.value = str
                    try {
                        str.toInt().also{a.value = str}
                    }
                    catch(e: Exception)
                    {

                    }
                }
            )
            Spacer(modifier = Modifier.width(40.dp))
            Text("B: ")
            TextField(
                value = b.value,
                onValueChange = {str ->
                    if (str.isEmpty()) b.value = str
                    if (str in listOf("-", "+")) b.value = str
                    try {
                        str.toInt().also{b.value = str}
                    }
                    catch(e: Exception)
                    {

                    }
                }
            )
        }
        Divider(modifier = Modifier.fillMaxWidth().border(2.dp, Black))
        Spacer(modifier = Modifier.height(40.dp))
        Text(myMax(a.value.toIntOrNull() ?: 0, b.value.toIntOrNull() ?: 0).toString())
    }
}

@Composable
fun Lab_2()
{

    class MyStack<T> : Iterable<T> where T: Comparable<T>
    {
        inner class MyNode<T>(var obj: T)
        {
            var next: MyNode<T>? = null
        }

        var frontNode : MyNode<T>? = null

        public fun push(obj: T)
        {
            var newHead = MyNode(obj)
            newHead.next = frontNode
            frontNode = newHead
        }

        public fun pop() : T
        {
            return (frontNode?.obj ?: throw NoSuchElementException()).also{frontNode = frontNode!!.next}
        }

        public fun min() : T
        {
            var min: T = this.frontNode?.obj ?: throw NoSuchElementException()
            this.forEach { if (min > it) min = it }
            return min
        }

        override fun iterator(): Iterator<T> = object : Iterator<T>{
            private var cursor = frontNode

            override fun hasNext(): Boolean {
                return cursor != null
            }

            override fun next(): T {
                return (cursor?.obj ?: throw NoSuchElementException()).also{cursor = cursor?.next}
            }

        }

    }

    @Composable fun Task1()
    {
        var textValue = remember{ mutableStateOf("") }
        Column(
        )
        {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.Bottom
            )
            {
                TextField(
                    modifier = Modifier
                        .twoColoredTemperatureBg(Yellow, Cyan),
                    value = textValue.value,
                    label = {Text("Введите скобки")},
                    onValueChange = { str ->
                        str.firstOrNull{it !in "(){}[]<>"}?.let{} ?: let{textValue.value = str}
                    }
                )
            }
            Spacer(modifier = Modifier.fillMaxWidth().height(1.dp).border(2.dp, Black))
            Spacer(modifier = Modifier.height(40.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.Bottom
            )
            {
                Box(
                    modifier = Modifier
                        .padding(10.dp)
                        .background(Color.Cyan)
                )
                {
                    Text(try {
                        MyStack<Char>().apply {
                            textValue.value.forEach {
                                when {
                                    (it in "([{") -> this.push(it)
                                    else -> {
                                        this.pop().also { openBr ->
                                            if ((openBr to it) !in listOf(
                                                    '(' to ')',
                                                    '[' to ']',
                                                    '{' to '}'
                                                )
                                            ) throw Exception("deffective")
                                        }
                                    }
                                }
                            }
                        }.let{st -> if (st.frontNode != null) throw Exception("surplus")}
                        "ПРАВИЛЬНО"
                    }
                    catch(e: Exception)
                    {
                        "НЕПРАВИЛЬНО"
                    })
                }
            }
        }
    }

    @Composable fun Task2()
    {
        Text(
            modifier = Modifier.scale(4f),
            text = "Стек реализован",
            color = Color.Green
        )
    }

    @Composable fun Task3()
    {
        fun getMaxList(lst: List<Int>, k: Int) : List<Int>
        {
            var resultMaxList = mutableListOf<Int>()
            var maximums = mutableListOf<Int>()
            repeat(k)
            {ind ->
                var newElem = lst[ind]
                val deleteSize = 0.toContainer().apply{maximums.indices.reversed().forEach { if (newElem >= maximums[it]) ++this.value }}.value
                if (deleteSize < maximums.size)
                {
                    maximums = (maximums.slice(0..maximums.size - 1 - deleteSize) + newElem).toMutableList()
                }
                else maximums = mutableListOf(newElem)
            }
            resultMaxList += maximums[0]
            repeat(lst.size - k)
            {ind ->
                var forgottenElem = lst[ind]
                var newElem = lst[k + ind]
                if (forgottenElem == maximums[0]) maximums = maximums.slice(1 until maximums.size).toMutableList()

                val deleteSize = 0.toContainer().apply{maximums.indices.reversed().forEach { if (newElem >= maximums[it]) ++this.value }}.value
                if (deleteSize < maximums.size)
                {
                    maximums = (maximums.slice(0..maximums.size - 1 - deleteSize) + newElem).toMutableList()
                }
                else maximums = mutableListOf(newElem)
                resultMaxList += maximums[0]
            }
            println()
            println()
            return resultMaxList.toList().also{it.forEach { elem -> print("$elem ") }}
        }

        var lst = remember { mutableStateOf("") }
        var k = remember { mutableStateOf("") }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        )
        {
            Row()
            {
                Text("Список: ")
                TextField(
                    value = lst.value,
                    onValueChange = {str ->
                        lst.value = str
                    }
                )
                Spacer(modifier = Modifier.width(40.dp))
                Text("K: ")
                TextField(
                    value = k.value,
                    onValueChange = {str ->
                        k.value = str
                    }
                )
            }
            Divider(modifier = Modifier.fillMaxWidth().border(2.dp, Black))
            Spacer(modifier = Modifier.height(40.dp))
            Text(
                try {
                    getMaxList(lst.value.split(" ").map(String::toInt), k.value.toIntOrNull() ?: 0).joinToString(" ")
                }
                catch (e: Exception)
                {
                    "nothing"
                })
        }
    }

    @Composable fun Task4()
    {
        fun getRepeatedNumbers(lst: List<out Int>) : List<Int>
        {
            var resultList = mutableListOf<Int>()
            var list = lst.toMutableList()

            return list.indices.forEach { ind ->
                list[Math.abs(list[ind])].let{
                    if (it < 0) resultList += Math.abs(list[ind])
                    else
                    {
                        list[Math.abs(list[ind])] = -it
                    }
                }
            }.run{resultList}
        }

        var textInput = remember{ mutableStateOf("") }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        )
        {
            TextField(
                modifier = Modifier.twoColoredTemperatureBg(Color.hsl(0f, 1f, 0.5f), Color.hsl(0f, 1f, 0.8f)),
                value = textInput.value,
                onValueChange = { str ->
                    textInput.value = str
                },
                label = {Text("Введите натуральные числа от 1..n-1")}
            )
            Divider(modifier = Modifier, startIndent = 8.dp, thickness = 2.dp, color = Black)
            Spacer(modifier = Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .padding(10.dp)
                    .twoColoredTemperatureBg(Color.hsl(120f, 1f, 0.5f), Color.hsl(130f, 1f, 0.7f))
            )
            {
                Text(try {
                    getRepeatedNumbers(textInput.value.split(" ").map{it.toIntOrNull() ?: 0}).joinToString(" ").let{if (it.isEmpty()) "nothing" else it}
                }                                                                                                                    //try{it[0].run{it}} catch(e: Exception){"nothing"}
                catch(e: Exception)                                                                                                  //it.isEmpty().takeIf{it1 -> it1}?.run{"nothing"} ?: it
                {
                    "one of elements greater than n"
                })
            }
        }
    }

    @Composable fun Task5()
    {
        var focusManager = LocalFocusManager.current
        var N = remember{ mutableStateOf("5") }
        var M = remember{ mutableStateOf("6") }
        var recomposeState = remember{ mutableStateOf(false) }
        var matrix = remember{ mutableStateOf(mutableListOf(mutableListOf<Int>())) }
        var matrixZeroed = remember{ mutableStateOf(mutableListOf(mutableListOf<Int>())) }


        LaunchedEffect(N.value, M.value, recomposeState.value)
        {
            val n = N.value.toIntOrNull() ?: 0
            val m = M.value.toIntOrNull() ?: 0
            matrix.value = MutableList(n) {i ->
                MutableList(m) {j ->
                    (Math.random() * 1000).toInt() % 10
                }
            }
            matrixZeroed.value = MutableList(n) {i ->
                MutableList(m) {j ->
                    matrix.value[i][j]
                }
            }

            (mutableSetOf<Int>() to mutableSetOf<Int>()).apply rowcolumns@{
                matrix.value.forEachIndexed { i, row ->
                    row.forEachIndexed { j, elem ->
                        if (elem == 0)
                        {
                            this.first += i
                            this.second += j
                        }
                    }
                }
            }.run{
                this.first.forEach { i ->
                    repeat(m) { j ->
                        //println("$i $j")
                        matrixZeroed.value[i][j] = 0
                    }
                }
                this.second.forEach { j ->
                    repeat(n) { i ->
                        //println("$i $j")
                        matrixZeroed.value[i][j] = 0
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit){
                                   detectTapGestures {
                                       focusManager.clearFocus()
                                   }
                },
            horizontalAlignment = Alignment.CenterHorizontally
        )
        {
            Spacer(modifier = Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            )
            {
                TextField(
                    modifier = Modifier.twoColoredTemperatureBg(Color.hsl(200f, 1f, 0.5f), Color.hsl(200f, 0.6f, 0.7f)),
                    value = N.value.toString(),
                    label = {Text("Введите N")},
                    onValueChange = {str ->
                        if (str.isEmpty()) N.value = ""
                        try{N.value = str.toInt().toString()} catch(e:Exception){}

                    }
                )
                Spacer(modifier = Modifier.width(40.dp))
                TextField(
                    modifier = Modifier.twoColoredTemperatureBg(Color.hsl(200f, 1f, 0.5f), Color.hsl(200f, 0.6f, 0.7f)),
                    value = M.value.toString(),
                    label = {Text("Введите M")},
                    onValueChange = {str ->
                        if (str.isEmpty()) M.value = ""
                        try{M.value = str.toInt().toString()} catch(e:Exception){}
                    }
                )
            }
            Divider(thickness = 2.dp, color = Color.Black)
            Spacer(modifier = Modifier.height(20.dp))
            OutlinedButton(
                onClick = {
                    recomposeState.value = !recomposeState.value
                }
            )
            {
                Text("Обновить")
            }
            Spacer(modifier = Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            )
            {
                Text(matrix.value.map{it.joinToString(" ")}.joinToString("\n"))
                Spacer(modifier = Modifier.width(40.dp))
                Text(matrixZeroed.value.map{it.joinToString(" ")}.joinToString("\n"))
            }
            Spacer(modifier = Modifier.weight(1f))
        }

    }

    var selectedTask = remember{ mutableStateOf(0) }

    Column(
        modifier = Modifier.fillMaxSize()
    )
    {
        TopAppBar(
            backgroundColor = Color.Magenta,
            elevation = 10.dp
        )
        {
            listOf("Задача 1", "Задача 2", "Задача 3", "Задача 4", "Задача 5").forEachIndexed{ind, it ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(CutCornerShape(10.dp))
                        .clickable {
                            selectedTask.value = ind
                        }
                        .background(if (ind == selectedTask.value) Color.Green else Color.hsl(80f, 1f, 0.5f))
                        .padding(10.dp)
                )
                {
                    Text(it)
                }
                Spacer(modifier = Modifier.width(20.dp))
            }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        )
        {
            listOf<@Composable () -> Unit>({Task1()}, {Task2()}, {Task3()}, {Task4()}, {Task5()})[selectedTask.value]()
        }
    }
}

@Composable
fun Practise_3()
{
    class TaskInfo(var content: @Composable () -> Unit, var midColor: Color, var sideColor: Color, var imagePath: String?, var name: String, var description: String)
    data class Ship(var name: String, var cannonballsRequired: Int, var treasury: Int)

    @Composable
    fun ColumnScope.OptionsSelector_1_2(wStr: MutableState<String>, targets: SnapshotStateList<Ship>)
    {
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        )
        {
            Spacer(Modifier.width(20.dp))
            TextField(
                value = wStr.value,
                label = {
                    Text("Введите количество ядер")
                },
                onValueChange = { str ->
                    if (str.isEmpty()) wStr.value = ""
                    try{str.toInt().takeIf{it > 0}?.also{wStr.value = str}} catch(e: Exception){}
                }
            )
            Spacer(Modifier.weight(1f))
            Column()
            {
                var expanded = remember { mutableStateOf(false) }
                var showAddDialog = remember { mutableStateOf(false) }
                if (showAddDialog.value)
                {
                    var shipName = remember { mutableStateOf("") }
                    var cost = remember { mutableStateOf("") }
                    var weight = remember { mutableStateOf("") }
                    AlertDialog(
                        modifier = Modifier
                            .background(
                                object : ShaderBrush(){
                                    override fun createShader(size: Size): Shader {
                                        return LinearGradientShader(Offset(size.width, 0f), Offset(0f, size.height), listOf(Color.hsl(150f, 1f, 0.5f), Color.hsl(120f, 0.5f, 0.4f)))
                                    }
                                }
                            ),
                        onDismissRequest = {
                            showAddDialog.value = false
                        },
                        dismissButton = {},
                        confirmButton = {},
                        text = {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            )
                            {
                                Spacer(Modifier.weight(1f))
                                TextField(
                                    modifier = Modifier.fillMaxWidth(),
                                    value = shipName.value,
                                    onValueChange = { str ->
                                        shipName.value = str
                                    },
                                    label = {
                                        Text("Введите название корабля")
                                    }
                                )
                                TextField(
                                    modifier = Modifier.fillMaxWidth(),
                                    value = weight.value,
                                    onValueChange = { str ->
                                        if (str.isEmpty()) weight.value = ""
                                        str.toIntOrNull()?.let{weight.value = str}
                                    },
                                    label = {
                                        Text("Введите количество ядер на корабль")
                                    }
                                )
                                TextField(
                                    modifier = Modifier.fillMaxWidth(),
                                    value = cost.value,
                                    onValueChange = { str ->
                                        if (str.isEmpty()) cost.value = ""
                                        str.toIntOrNull()?.let{cost.value = str}
                                    },
                                    label = {
                                        Text("Введите цену сокровищ корабля")
                                    }
                                )
                                Spacer(Modifier.weight(4f))
                                Box(
                                    modifier = Modifier
                                        .twoColoredTemperatureBg(Color.Red, Color.Blue)
                                        .clickable {
                                            targets += Ship(shipName.value, weight.value.toIntOrNull() ?: 0, cost.value.toIntOrNull() ?: 0)
                                            showAddDialog.value = false
                                        }
                                        .fillMaxWidth()
                                        .height(70.dp),
                                    contentAlignment = Alignment.Center
                                )
                                {
                                    Text("Добавить", color = Color.White)
                                }
                            }
                        }
                    )
                }
                Box(
                    modifier = Modifier
                        .clickable {
                            expanded.value = true
                        }
                        .twoColoredTemperatureBg(Color.Yellow, Color.Magenta)
                        .height(30.dp)
                        .width(150.dp),
                    contentAlignment = Alignment.Center
                )
                {
                    Text("Типы кораблей")
                }
                DropdownMenu(
                    modifier = Modifier
                        .background(
                            object : ShaderBrush(){
                                override fun createShader(size: Size): Shader {
                                    return LinearGradientShader(Offset(0f, 0f), Offset(size.width, size.height), listOf(Color.hsl(270f, 1f, 0.5f), Color.hsl(240f, 0.5f, 0.4f)))
                                }
                            }
                        )
                        .fillMaxSize(0.6f),
                    expanded = expanded.value,
                    onDismissRequest = {
                        expanded.value = false
                    }
                )
                {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    )
                    {
                        targets.forEach {ship ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                verticalAlignment = Alignment.CenterVertically
                            )
                            {
                                Spacer(Modifier.width(10.dp))
                                Text(ship.name, color = Color.Red)
                                Spacer(Modifier.width(10.dp))
                                Divider(thickness = 2.dp, modifier = Modifier.width(2.dp).fillMaxHeight(), color = Color.Yellow)
                                Spacer(Modifier.width(10.dp))
                                Text("w: ${ship.cannonballsRequired}")
                                Spacer(Modifier.width(10.dp))
                                Divider(thickness = 2.dp, modifier = Modifier.width(2.dp).fillMaxHeight(), color = Color.Yellow)
                                Spacer(Modifier.width(10.dp))
                                Text("c: ${ship.treasury}")
                                Spacer(Modifier.width(10.dp))
                                Divider(thickness = 2.dp, modifier = Modifier.width(2.dp).fillMaxHeight(), color = Color.Magenta)
                                Spacer(Modifier.weight(1f))
                                Box(
                                    modifier = Modifier
                                        .clickable {
                                            targets.remove(ship)
                                        }
                                        .background(Color.Red)
                                        .height(46.dp)
                                        .width(46.dp),
                                    contentAlignment = Alignment.Center
                                )
                                {
                                    Text("-", modifier = Modifier.scale(1.5f))
                                }
                                Spacer(Modifier.width(10.dp))
                            }
                            Divider(thickness = 2.dp, color = Color.hsl(15f, 1f, 0.5f))
                        }
                    }
                    Box(
                        modifier = Modifier
                            .clickable {
                                showAddDialog.value = true
                            }
                            .background(Color.Green)
                            .fillMaxWidth()
                            .height(30.dp),
                        contentAlignment = Alignment.Center
                    )
                    {
                        Text(text = "+", modifier = Modifier.scale(2f))
                    }
                }
            }
            Spacer(Modifier.width(20.dp))
        }
        Spacer(Modifier.weight(1f))
    }

    @Composable
    fun Task1(setSelectedTask: (Int?) -> Unit)
    {
        var targets = remember{ mutableStateListOf<Ship>(
            Ship("Sloop", 8, 8),
            Ship("Brigantine", 11, 10),
            Ship("Galleon", 21, 23)// 27 cannonballs, then what? 1) 1 galeon 0 brig 0 sloop; 2) 0 galeon 1 brig 2 sloop
        )}
        var wStr = remember{ mutableStateOf("") }
        var arrResult = remember{ mutableStateOf<List<Int>>(listOf()) }

        LaunchedEffect(targets.size, wStr.value)
        {
            var w = wStr.value.toIntOrNull() ?: 0
            var tempRes = mutableListOf(0)
            repeat(w)
            {it1 ->
                (it1 + 1).let{ ind ->
                    tempRes += targets.maxOfOrNull { try { (tempRes[ind - it.cannonballsRequired] + it.treasury) } catch (e: Exception) { 0 }} ?: 0
                }
            }
            arrResult.value = tempRes
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        )
        {
            OptionsSelector_1_2(wStr, targets)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .height(70.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Start
            )
            {
                Column(
                    modifier = Modifier
                        .width(100.dp)
                        .height(70.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                )
                {
                    Text("кол-во ядер")
                    Divider(thickness = 2.dp, color = Color.Green)
                    Text("сокровища")
                }
                Divider(modifier = Modifier.width(2.dp).fillMaxHeight(), color = Color.Green)
                arrResult.value.forEachIndexed { ind, res ->
                    Column(
                        modifier = Modifier
                            .width(50.dp)
                            .height(70.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    )
                    {
                        Text(ind.toString())
                        Divider(thickness = 2.dp, color = Color.Green)
                        Text(res.toString(), color = when {
                            (ind >= arrResult.value.size - 1) -> Color.Magenta
                            (res > 0) -> Color.Cyan
                            else -> Color.White
                        })
                    }
                    Divider(modifier = Modifier.width(2.dp).fillMaxHeight(), color = Color.Green)
                }
            }
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clickable {
                        setSelectedTask(null)
                    }
                    .twoColoredTemperatureBg(Color.White, Color.Black)
                    .fillMaxWidth(0.95f)
                    .height(50.dp),
                contentAlignment = Alignment.Center
            )
            {
                Text("НАЗАД", color = Color.White)
            }
            Spacer(Modifier.height(20.dp))
        }
    }

    @Composable
    fun Task2(setSelectedTask: (Int?) -> Unit)
    {
        var targets = remember{ mutableStateListOf<Ship>(
            Ship("Sloop", 8, 8),
            Ship("Brigantine", 11, 10),
            Ship("Galleon", 21, 23)// 27 cannonballs, then what? 1) 1 galeon 0 brig 0 sloop; 2) 0 galeon 1 brig 2 sloop
        )}
        var wStr = remember{ mutableStateOf("") }
        var arrResult = remember{ mutableStateOf<List<List<Int>>>(listOf()) }

        LaunchedEffect(targets.size, wStr.value)
        {
            var w = wStr.value.toIntOrNull() ?: 0
            var tempRes = MutableList(w + 1){MutableList(targets.size + 1){0} }
            repeat(w + 1) { wInd ->
                repeat(targets.size + 1) { iInd ->
                    tempRes[wInd][iInd] = Math.max(
                        try{
                            tempRes[wInd][iInd - 1]
                        }
                        catch (e: Exception){
                            0
                        },
                        try{
                            tempRes[wInd - targets[iInd - 1].cannonballsRequired][iInd - 1] + targets[iInd - 1].treasury
                        }
                        catch (e: Exception){
                            0
                        }
                    )
                }
            }
            arrResult.value = tempRes
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        )
        {
            OptionsSelector_1_2(wStr, targets)
            if (arrResult.value.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .weight(8f)
                )
                {
                    Row(

                    )
                    {
                        Text(
                            "w\\i",
                            modifier = Modifier.background(Color.Blue).padding(10.dp).size(40.dp, 30.dp),
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.width(2.dp))
                        repeat(arrResult.value[0].size) { itInd ->
                            Text(
                                "$itInd",
                                modifier = Modifier.background(Color.Blue).padding(10.dp).size(35.dp, 30.dp),
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.width(2.dp))
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    repeat(arrResult.value.size) { wInd ->
                        Row(

                        )
                        {
                            Text(
                                "$wInd",
                                modifier = Modifier.background(Color.Blue).padding(10.dp).size(40.dp, 30.dp),
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.width(2.dp))
                            repeat(arrResult.value[wInd].size) { iInd ->
                                Text(
                                    "${arrResult.value[wInd][iInd]}",
                                    modifier = Modifier.background(Color.Green).padding(10.dp).size(35.dp, 30.dp),
                                    color = Color.White,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.width(2.dp))
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clickable {
                        setSelectedTask(null)
                    }
                    .twoColoredTemperatureBg(Color.White, Color.Black)
                    .fillMaxWidth(0.95f)
                    .height(50.dp),
                contentAlignment = Alignment.Center
            )
            {
                Text("НАЗАД", color = Color.White)
            }
            Spacer(Modifier.height(20.dp))
        }
    }

    var selectedTask = remember { mutableStateOf<Int?>(null) }

    var tasks = listOf<TaskInfo>(
        TaskInfo(
            {
                Task1(selectedTask.component2())
            },
            Color.Blue,
            Color.Cyan,
            "task1.jpg",
            "Задача с повторениями",
            "На пути пиратского корабля множество призрачных кораблей.\nКаждый корабль может выдержать ровно Wi выстрелов из пушки\nКорабли несут на себе сокровища стоимостью Ci\nКак пиратам выбрать цели, чтобы получить макс. сокровища при W ядрах"
        ),
        TaskInfo(
            {
                Task2(selectedTask.component2())
            },
            Color.Red,
            Color.hsv(20f, 0.5f, 0.6f),
            "task2.jpg",
            "Задача без повторений",
            "На пути пиратского корабля N кораблей(каждый по 1 типу).\nКаждый корабль может выдержать ровно Wi выстрелов из пушки\nКорабли несут на себе сокровища стоимостью Ci\nКак пиратам выбрать цели, чтобы получить макс. сокровища при W ядрах"
        )
    )


    @Composable
    fun myAnimated(value: Float) : Float
    {
        var hunter = remember { mutableStateOf(value) }
        LaunchedEffect(value)
        {
            while(true) {
                if (hunter.value != value)
                {
                    hunter.value += (value - hunter.value) / 40
                    if (abs(hunter.value - value) <= 0.01) hunter.value = value
                    //println(hunter.value)
                }
                try {
                    delay(10)
                } catch (e: Exception) {
                    //println(e.message)
                    throw e
                }
            }
        }
        return hunter.value
    }

    selectedTask.value?.let{
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        )
        {
            tasks[selectedTask.value!!].content()
        }
    } ?: 0.let{
        var pendingTask = remember { mutableStateOf(0) }
        Row(
            modifier = Modifier.fillMaxSize()
        )
        {
            var imgSize = remember { mutableStateOf(IntSize(0, 0)) }
            tasks.forEachIndexed{ ind, elem ->
                var animWeight = animateFloatAsState(if (ind == pendingTask.value) 3f else 1f, tween(700, easing = FastOutSlowInEasing)).value
                var iSO = remember { MutableInteractionSource() }
                var howered = iSO.collectIsHoveredAsState().value
                Column(
                    modifier = Modifier
                        .twoColoredTemperatureBg(elem.sideColor, elem.midColor)
                        .fillMaxHeight()
                        //.weight(myAnimated(if (ind == pendingTask.value) 3f else 1f))
                        .weight(animWeight)
                        .hoverable(
                            interactionSource = iSO,
                            enabled = true
                        )
                        .clickable {
                                   selectedTask.value = ind
                        },
                    horizontalAlignment = Alignment.CenterHorizontally
                )
                {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(text = elem.name, fontSize = TextUnit(27f, TextUnitType(27)))
                    Divider(thickness = 2.dp)
                    Spacer(Modifier.width(40.dp))
                    if (animWeight == 3f) {
                        Image(
                            modifier = Modifier
                                .onSizeChanged { newSize ->
                                    imgSize.value = newSize
                                }
                                .fillMaxWidth(1f),
                            painter = painterResource(elem.imagePath ?: ""),
                            contentDescription = null
                        )
                    }
                    else{
                        Image(
                            modifier = Modifier
                                .horizontalScroll(ScrollState(((3f - animWeight) * imgSize.value.width / 4).toInt()))
                                .height(imgSize.value.height.dp),
                            painter = painterResource(elem.imagePath ?: ""),
                            contentDescription = null
                        )
                    }
                    Spacer(Modifier.width(20.dp))
                    if (animWeight == 3f) Text(text = elem.description, fontSize = TextUnit(17f, TextUnitType(15)), color = Color.Yellow)
                    Spacer(Modifier.width(20.dp))
                }
                LaunchedEffect(howered)
                {
                    if (howered)
                    {
                        pendingTask.value = ind
                    }
                }
            }
        }
    }
}

@Composable
fun Lab_3()
{
    @Composable
    fun Task_1()
    {
        var inpString = remember{ mutableStateOf("") }
        Column(
            modifier = Modifier
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        )
        {
            TextField(
                modifier = Modifier
                    .twoColoredTemperatureBg(Color.White, Color.Magenta),
                value = inpString.value,
                label = { Text("Введите строку") },
                onValueChange = {str ->
                    inpString.value = str
                }
            )
            Divider(thickness = 2.dp)
            Spacer(Modifier.height(20.dp))
            Text("Перевернутая строка: ${
                try{
                    inpString.value.run str@{mutableListOf<Char>().apply{ (0..this@str.lastIndex).reversed().forEach { ind -> this += this@str[ind] } }.joinToString("")}.also{println(it)}
                }
                catch(e: Exception){
                    ""
                }
            }")
        }
    }

    @Composable
    fun Task_2()
    {
        var inpString = remember{ mutableStateOf("") }
        var inpWord = remember{ mutableStateOf("") }
        Column(
            modifier = Modifier
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        )
        {
            TextField(
                modifier = Modifier
                    .twoColoredTemperatureBg(Color.White, Color.Magenta),
                singleLine = false,
                value = inpString.value,
                label = { Text("Введите текст") },
                onValueChange = {str ->
                    inpString.value = str
                }
            )
            Spacer(Modifier.height(20.dp))
            TextField(
                modifier = Modifier
                    .twoColoredTemperatureBg(Color.White, Color.Magenta),
                value = inpWord.value,
                singleLine = true,
                label = { Text("Введите слово") },
                onValueChange = {str ->
                    inpWord.value = str
                }
            )
            Divider(thickness = 2.dp)
            Spacer(modifier = Modifier.height(40.dp))
            Row(
                modifier = Modifier
                    .padding(10.dp)
                    .fillMaxWidth(0.6f)
                    .twoColoredTemperatureBg(Color.hsl(60f, 1f, 0.3f), Color.hsl(60f, 1f, 0.6f))
                    .horizontalScroll(rememberScrollState())
            )
            {
                inpString.value.split("\n").forEach {word ->
                    infix fun String.anagram(b: String): Boolean{
                        return fun String.(): Map<Char, Int> {
                            return mutableMapOf<Char, Int>().also{m -> this.forEach { m[it] = (m[it] ?: 0) + 1 }}
                        }.let{ func ->
                            (func.invoke(this) to func.invoke(b)).run mPair@{
                                this.first == this.second
                            }
                        }
                    }

                    if (word anagram inpWord.value)
                    {
                        Text(word)
                        Spacer(Modifier.width(20.dp))
                    }
                }
            }
        }
    }

    @Composable
    fun Task_3()
    {
        var inpString = remember{ mutableStateOf("") }
        Column(
            modifier = Modifier
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        )
        {
            TextField(
                modifier = Modifier
                    .twoColoredTemperatureBg(Color.White, Color.Magenta),
                value = inpString.value,
                label = { Text("Введите строку") },
                onValueChange = {str ->
                    inpString.value = str
                }
            )
            Divider(thickness = 2.dp)
            Spacer(Modifier.height(20.dp))
            Text("Сокращенная строка: ${
                try{
                    mutableListOf<String>().apply seq@{
                        var currentChar = inpString.value[0]
                        var currentCount = 0
                        inpString.value.forEach { ch -> if (currentChar == ch) {++currentCount} else {this += currentChar.toString(); this += currentCount.toString(); currentChar = ch; currentCount = 1} }
                        this += currentChar.toString()
                        this += currentCount.toString()
                    }.joinToString("")
                }
                catch(e: Exception){
                    ""
                }
            }")
        }
    }

    @Composable
    fun Task_4()
    {
        var nStr = remember{ mutableStateOf("") }
        var N = nStr.value.toIntOrNull() ?: 3
        val thickness = 20.dp
        var recompositionValue = remember{ mutableStateOf(false) }
        recompositionValue.value

        var matrixAdjacency: MutableList<MutableList<Int>> = MutableList(N){ mutableListOf() }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        )
        {
            TextField(
                label = {Text("Введите N")},
                value = nStr.value,
                onValueChange = { str ->
                    if (str.isEmpty()) nStr.value = ""
                    str.toIntOrNull()?.let{nStr.value = str}
                }
            )
            Spacer(Modifier.height(10.dp))
            TextButton(
                onClick = {
                    recompositionValue.value = !recompositionValue.value
                }
            )
            {
                Text("Обновить")
            }
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth())
            {
                Text(text = "*", modifier = Modifier.width(thickness))
                Divider(modifier = Modifier.height(thickness).width(2.dp), thickness = 2.dp, color = Color.Black)
                repeat(N)
                {
                    Text(text = (it + 1).toString(), modifier = Modifier.width(thickness))
                }
            }
            Divider(thickness = 2.dp, color = Color.Black)
            Row(modifier = Modifier.fillMaxWidth())
            {
                Column(modifier = Modifier.width(thickness))
                {
                    repeat(N)
                    {
                        Text((it + 1).toString())
                    }
                }
                Divider(modifier = Modifier.fillMaxHeight().width(2.dp), thickness = 2.dp, color = Color.Black)
                Column(){
                    repeat(N)
                    {i ->
                        Row()
                        {
                            repeat(N)
                            {j ->
                                val isConnection = if (i == j) 0 else (Math.random() * 1000).toInt() % 2
                                matrixAdjacency[i] += isConnection
                                Text(text = isConnection.toString(), modifier = Modifier.width(thickness))
                            }
                        }
                    }
                }
            }
            fun<T> BFS(elements: MutableList<T>, matrix: MutableList<MutableList<Int>>, start: Int, action: (T) -> Unit)
            {
                var visited = MutableList(elements.size){false}
                var wave = setOf(start)
                action(elements[start])
                visited[start] = true
                while(wave.isNotEmpty())
                {
                    var newWave = mutableSetOf<Int>()
                    wave.forEach { elemInd ->
                        matrix[elemInd].forEachIndexed { linkInd, isConn ->
                            if (isConn == 1 && !visited[linkInd])
                            {
                                newWave += linkInd.also{visited[linkInd] = true}
                            }
                        }
                    }
                    newWave.forEach { action(elements[it]) }
                    wave = newWave
                }
            }
            BFS((1..N).toMutableList(), matrixAdjacency, 0, {a-> println(a)})
            println()
        }
    }

    var questions = mutableMapOf<String, @Composable () -> Unit>("Задача 1" to @Composable{Task_1()}, "Задача 2" to @Composable{Task_2()}, "Задача 3" to @Composable{Task_3()}, "Задача 4" to @Composable{Task_4()})
    var selectedQuestion = remember{ mutableStateOf("Задача 1") }
    Column(
        modifier = Modifier.fillMaxSize()
    )
    {
        TopAppBar(
            modifier = Modifier
                .fillMaxWidth()
                .twoColoredTemperatureBg(Color.Green, Color.Yellow),
            elevation = 4.dp,
            contentPadding = PaddingValues(10.dp)
        )
        {
            questions.forEach {
                Box(
                    modifier = Modifier
                        .clip(CutCornerShape(10.dp))
                        .twoColoredTemperatureBg(if (it.key == selectedQuestion.value) Color.Blue else Color.Yellow, Color.White)
                        .clickable(
                            onClick = {
                                selectedQuestion.value = it.key
                            }
                        )
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                )
                {
                    Text(it.key, color = Color.Black)
                }
                Spacer(modifier = Modifier.width(40.dp))
            }
        }
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        )
        {
            questions[selectedQuestion.value]?.invoke()
        }
    }
}
//ENDREGION MENUPAGES

data class MenuOption(var text: String, var animatedSelectedColor: MutableState<Float>, var completed: Boolean?, var content: @Composable () -> Unit)

class Data
{
    companion object
    {
        var sortMethods = mutableMapOf(
            "Шейкерная сортировка" to {a: MutableList<Int> -> a.ShakerSorted()},
            "Быстрая сортировка" to {a: MutableList<Int> -> a.QuickSorted()},
            "Быстрая сортировка 1" to {a: MutableList<Int> -> a.QuickSorted1({a, b -> a - b})},
        )

        var navigationMenuOptions : List<MenuOption> = listOf(
            MenuOption("О Программе", mutableStateOf(0f), null, {InfoPage()}),
            MenuOption("1 практика", mutableStateOf(0f), true, {Practise_1()}),
            MenuOption("1 лабораторная", mutableStateOf(0f), true, {Lab_1()}),
            MenuOption("2 практика", mutableStateOf(0f), true, {Practise_2()}),
            MenuOption("2 лабораторная", mutableStateOf(0f), true, {Lab_2()}),
            MenuOption("3 практика", mutableStateOf(0f), true, {Practise_3()}),
            MenuOption("3 лабораторная", mutableStateOf(0f), true, {Lab_3()})
        )
    }
}


//REGION MAIN

@OptIn(ExperimentalGraphicsApi::class)
@Composable
fun App() {
    var selectedIndex = remember{ mutableStateOf(-1) }
    LaunchedEffect(Unit)
    {
        while(true)
        {
            Data.navigationMenuOptions.forEachIndexed { ind, it ->
                if (ind == selectedIndex.value)
                {
                    it.animatedSelectedColor.value = Math.min(1f, it.animatedSelectedColor.value + 0.02f)
                }
                else
                {
                    it.animatedSelectedColor.value = Math.max(0f, it.animatedSelectedColor.value - 0.02f)
                }
            }
            delay(10)
        }
    }
    Row(
        modifier = Modifier
            .fillMaxSize()
    )
    {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(180.dp)
                .background(Color.DarkGray)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        )
        {
            Spacer(modifier = Modifier.height(40.dp))
            Data.navigationMenuOptions.forEachIndexed { ind, menuElement ->
                GradientalBox(
                    modifier = Modifier
                        .size(150.dp, 80.dp)
                        .pointerInput(Unit){
                            this.detectTapGestures {
                                selectedIndex.value = ind
                            }
                        },
                    centerColor = when(menuElement.completed){null -> Color.Gray; false -> Color.Red; true -> Color.Green},
                    sideColor = Color.hsl(180f, 1f, 0.2f + menuElement.animatedSelectedColor.value / 3f)
                )
                {
                    Text(
                        text = menuElement.text,
                        style = TextStyle(
                            color = if (selectedIndex.value == ind) Magenta else Color.Blue
                        )
                    )
                }
                if (ind < Data.navigationMenuOptions.size - 1) Spacer(modifier = Modifier.height(60.dp))
            }
            Spacer(modifier = Modifier.height(40.dp))
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(object : ShaderBrush(){
                    override fun createShader(size: Size): Shader {
                        return LinearGradientShader(Offset(0f, 0f), Offset(size.width, size.height * 2 / 3), listOf(LightGray, DarkGray))
                    }

                })
        )
        {
            if (selectedIndex.value >= 0) Data.navigationMenuOptions[selectedIndex.value].content()
        }
    }
}

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Задания по алгоритмам"
    ) {
        App()
    }
}
//ENDREGION MAIN