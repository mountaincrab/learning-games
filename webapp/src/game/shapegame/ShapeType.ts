/** The shapes a child matches in the Magic Hat game. There are twelve so a full
 * game shows every shape once, in a randomised arrangement. */
export enum ShapeType {
  CIRCLE = 'CIRCLE',
  SQUARE = 'SQUARE',
  TRIANGLE = 'TRIANGLE',
  PENTAGON = 'PENTAGON',
  DIAMOND = 'DIAMOND',
  RECTANGLE = 'RECTANGLE',
  DOME = 'DOME',
  STAR = 'STAR',
  HEXAGON = 'HEXAGON',
  HEART = 'HEART',
  OVAL = 'OVAL',
  CROSS = 'CROSS',
}

export const ALL_SHAPE_TYPES: ShapeType[] = Object.values(ShapeType)

export const SHAPE_FILL_COLOR: Record<ShapeType, string> = {
  [ShapeType.CIRCLE]: '#FFD23F',
  [ShapeType.SQUARE]: '#4ECDC4',
  [ShapeType.TRIANGLE]: '#6FBF3B',
  [ShapeType.PENTAGON]: '#9B5DE5',
  [ShapeType.DIAMOND]: '#FF6B6B',
  [ShapeType.RECTANGLE]: '#577590',
  [ShapeType.DOME]: '#F4A261',
  [ShapeType.STAR]: '#FFB703',
  [ShapeType.HEXAGON]: '#00BBF9',
  [ShapeType.HEART]: '#F15BB5',
  [ShapeType.OVAL]: '#80ED99',
  [ShapeType.CROSS]: '#E07A5F',
}
