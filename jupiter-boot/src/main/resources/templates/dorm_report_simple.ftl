<#list blocks as block>
    ${date} ${college} ${block.building}宿舍楼查宿情况
    <#if showSummary>
        应到:${block.summary.should?string("0")?left_pad(5)}人  实到:${block.summary.actual?string("0")?left_pad(5)}人  请假:${block.summary.leave?string("0")?left_pad(4)}人<#if block.summary.dayStudent gt 0>  走读:${block.summary.dayStudent?string("0")?left_pad(4)}人</#if>
    </#if>
    <#if showFloorDetail>
        <#list block.floors as floor>
            ${floor.floor?right_pad(4)}  应到:${floor.should?string("0")?left_pad(5)}人  实到:${floor.actual?string("0")?left_pad(5)}人<#if floor.leave gt 0>  请假:${floor.leave?string("0")?left_pad(4)}人</#if><#if floor.intern gt 0>  实习:${floor.intern?string("0")?left_pad(4)}人</#if>
        </#list>
    </#if>

</#list>
<#if showNewStudent && newStudent??>
    新生数据
    应到:${newStudent.should?string("0")?left_pad(5)}人  实到:${newStudent.actual?string("0")?left_pad(5)}人
    男生:${newStudent.male?string("0")?left_pad(5)}人  女生:${newStudent.female?string("0")?left_pad(5)}人
    征兵:${newStudent.military?string("0")?left_pad(5)}人  请假:${newStudent.leave?string("0")?left_pad(5)}人
</#if>