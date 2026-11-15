/** Only requests to change the editor enter the write flow; explanations remain chat messages. */
export function shouldAutoEditCode(text: string, isAdmin: boolean): boolean {
  const value = text.trim().replace(/\s+/g, '')
  if (!value) return false
  if (/(修改|修复|改正|改一下|帮我改|重写|优化|写入|替换|写进|自动改).{0,20}(代码|程序|编辑器|这段)|(代码|程序|编辑器).{0,14}(修改|修复|改正|重写|优化|写入|替换|写进)/.test(value)) return true
  if (/(自动写|写入|写进|填入|替换).{0,12}(答案|编辑器)/.test(value)) return true
  return isAdmin && (/(给我|生成|写出|写一份|帮我写|直接给|我要|提供).{0,10}(完整|正确|参考)?(答案|参考代码|完整代码|可运行代码|完整程序)/.test(value)
    || /帮我写.{0,8}代码/.test(value))
}
